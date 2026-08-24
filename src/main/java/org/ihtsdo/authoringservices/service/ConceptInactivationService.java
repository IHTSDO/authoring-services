package org.ihtsdo.authoringservices.service;

import org.ihtsdo.authoringservices.domain.ConceptInactivationRequest;
import org.ihtsdo.authoringservices.domain.ConceptInactivationRequest.AcceptedAffectedConcept;
import org.ihtsdo.authoringservices.domain.ConceptInactivationRequest.AcceptedAffectedHistoricalAssociation;
import org.ihtsdo.authoringservices.domain.ConceptInactivationRequest.AcceptedReplacement;
import org.ihtsdo.authoringservices.domain.ConceptInactivationRequest.Association;
import org.ihtsdo.authoringservices.domain.CrsBlockingState;
import org.ihtsdo.authoringservices.domain.EntityType;
import org.ihtsdo.authoringservices.domain.Notification;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ConceptPojo.HistoricalAssociation;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ConceptPojo.InactivationIndicator;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ConceptInactivationService {

	static final String CRS_BLOCKED_MESSAGE =
			"Concept inactivation is blocked because this task has unsaved CRS concepts with SCTIDs.";
	private static final String CONCEPT_INACTIVATED_MESSAGE = "Concept %s inactivated";

	private final PermissionService permissionService;
	private final ContentRequestService contentRequestService;
	private final BranchService branchService;
	private final SnowstormRestClientFactory snowstormRestClientFactory;
	private final NotificationService notificationService;

	public ConceptInactivationService(PermissionService permissionService,
			ContentRequestService contentRequestService,
			BranchService branchService,
			SnowstormRestClientFactory snowstormRestClientFactory,
			NotificationService notificationService) {
		this.permissionService = permissionService;
		this.contentRequestService = contentRequestService;
		this.branchService = branchService;
		this.snowstormRestClientFactory = snowstormRestClientFactory;
		this.notificationService = notificationService;
	}

	public List<ConceptPojo> inactivate(String projectKey, String taskKey, String conceptId,
			ConceptInactivationRequest request, Boolean dryRunParam) throws BusinessServiceException {
		ConceptInactivationRequest effectiveRequest = request != null ? request : new ConceptInactivationRequest();
		if (!StringUtils.hasLength(conceptId)) {
			throw new IllegalArgumentException("Parameter conceptId is required.");
		}
		if (!StringUtils.hasLength(effectiveRequest.getReasonId())) {
			throw new IllegalArgumentException("reasonId is required.");
		}

		permissionService.checkFullPermissionOnProjectOrThrow(projectKey);

		String username = SecurityUtil.getUsername();
		CrsBlockingState crsBlockingState = contentRequestService.getBlockingState(projectKey, taskKey, username);
		if (crsBlockingState.isBlocked()) {
			throw new BusinessServiceException(CRS_BLOCKED_MESSAGE);
		}

		boolean dryRun = resolveDryRun(effectiveRequest, dryRunParam);
		String branchPath = branchService.getTaskBranchPathUsingCache(projectKey, taskKey);
		SnowstormRestClient client = snowstormRestClientFactory.getClient();

		try {
			List<ConceptPojo> conceptsToUpdate = prepareConceptsForUpdate(client, branchPath, conceptId, effectiveRequest);
			if (dryRun) {
				return conceptsToUpdate;
			}

			client.bulkUpdateConcepts(branchPath, conceptsToUpdate);
			emitCompletionNotification(projectKey, taskKey, branchPath, conceptId);
			Set<String> updatedConceptIds = conceptsToUpdate.stream().map(ConceptPojo::getConceptId).collect(Collectors.toSet());
			return client.searchConcepts(branchPath, new ArrayList<>(updatedConceptIds));
		} catch (RestClientException e) {
			throw new BusinessServiceException("Failed to inactivate concept " + conceptId, e);
		}
	}

	private List<ConceptPojo> prepareConceptsForUpdate(SnowstormRestClient client, String branchPath,
			String conceptId, ConceptInactivationRequest request) throws RestClientException, BusinessServiceException {
		List<AcceptedAffectedHistoricalAssociation> historicalUpdates =
				request.getAcceptedAffectedHistoricalAssociations();

		Set<String> conceptIds = collectRelatedConceptIds(conceptId, request, historicalUpdates);
		// Fetch full concepts (including inactive concepts/descriptions and their associationTargets).
		List<ConceptPojo> fetched = client.searchConcepts(branchPath, new ArrayList<>(conceptIds));
		Map<String, ConceptPojo> conceptsById = indexByConceptId(fetched);

		ConceptPojo inactivationConcept = conceptsById.get(conceptId);
		if (inactivationConcept == null) {
			throw new BusinessServiceException("Concept " + conceptId + " not found on branch " + branchPath);
		}
		applyInactivation(inactivationConcept, request);

		applyAcceptedAffectedConceptUpdates(branchPath, conceptId, request, conceptsById);

		applyHistoricalAssociationUpdates(conceptsById, conceptId, request.getReasonId(), historicalUpdates);

		return buildConceptsToUpdate(conceptIds, conceptsById, conceptId, inactivationConcept);
	}

	private void applyAcceptedAffectedConceptUpdates(String branchPath, String conceptId, ConceptInactivationRequest request, Map<String, ConceptPojo> conceptsById) throws BusinessServiceException {
		for (AcceptedAffectedConcept accepted : request.getAcceptedAffectedConcepts()) {
			if (accepted == null || !StringUtils.hasLength(accepted.getConceptId())) {
				continue;
			}
			ConceptPojo affected = conceptsById.get(accepted.getConceptId());
			if (affected == null) {
				throw new BusinessServiceException(
						"Accepted affected concept " + accepted.getConceptId() + " not found on branch " + branchPath);
			}
			applyAcceptedReplacements(affected, conceptId, accepted.getAcceptedReplacements());
		}
	}

	private void applyHistoricalAssociationUpdates(Map<String, ConceptPojo> conceptsById, String inactivatedConceptId,
			String reasonId, List<AcceptedAffectedHistoricalAssociation> updates) throws BusinessServiceException {
		if (CollectionUtils.isEmpty(updates)) {
			return;
		}
		Map<String, List<AcceptedAffectedHistoricalAssociation>> byConcept = new LinkedHashMap<>();
		Map<String, List<AcceptedAffectedHistoricalAssociation>> byDescription = new LinkedHashMap<>();
		for (AcceptedAffectedHistoricalAssociation update : updates) {
			if (update == null || !StringUtils.hasLength(update.getConceptId())) {
				continue;
			}
			if (StringUtils.hasLength(update.getDescriptionId())) {
				byDescription.computeIfAbsent(update.getDescriptionId(), ignored -> new ArrayList<>()).add(update);
			} else {
				byConcept.computeIfAbsent(update.getConceptId(), ignored -> new ArrayList<>()).add(update);
			}
		}

		InactivationIndicator reasonIndicator = resolveInactivationIndicator(reasonId);
		for (Map.Entry<String, List<AcceptedAffectedHistoricalAssociation>> entry : byConcept.entrySet()) {
			ConceptPojo concept = conceptsById.get(entry.getKey());
			if (concept == null) {
				throw new BusinessServiceException(
						"Historical association affected concept " + entry.getKey() + " not found");
			}
			applyConceptHistoricalAssociations(concept, inactivatedConceptId, reasonIndicator, entry.getValue());
		}
		for (Map.Entry<String, List<AcceptedAffectedHistoricalAssociation>> entry : byDescription.entrySet()) {
			AcceptedAffectedHistoricalAssociation first = entry.getValue().get(0);
			ConceptPojo concept = conceptsById.get(first.getConceptId());
			if (concept == null) {
				throw new BusinessServiceException(
						"Historical association affected concept " + first.getConceptId() + " not found");
			}
			applyDescriptionHistoricalAssociations(concept, entry.getKey(), inactivatedConceptId, entry.getValue());
		}
	}

	private static void applyConceptHistoricalAssociations(ConceptPojo concept, String inactivatedConceptId,
			InactivationIndicator reasonIndicator, List<AcceptedAffectedHistoricalAssociation> updates)
			throws BusinessServiceException {
		InactivationIndicator oldIndicator = concept.getInactivationIndicator();
		InactivationIndicator newIndicator = requireHistoricalInactivationIndicator(updates);
		List<Map<HistoricalAssociation, Set<String>>> rowTargets = new ArrayList<>();
		for (AcceptedAffectedHistoricalAssociation update : updates) {
			Map<HistoricalAssociation, Set<String>> working = copyAssociationTargets(concept.getAssociationTargets());
			removeAssociationTarget(working, inactivatedConceptId);
			applyHistoricalAssociationRow(working, update, reasonIndicator, oldIndicator, false);
			rowTargets.add(working);
		}
		concept.setAssociationTargets(mergeAssociationTargets(rowTargets));
		concept.setInactivationIndicator(newIndicator);
	}

	private static void applyDescriptionHistoricalAssociations(ConceptPojo concept, String descriptionId,
			String inactivatedConceptId, List<AcceptedAffectedHistoricalAssociation> updates)
			throws BusinessServiceException {
		DescriptionPojo description = findDescription(concept, descriptionId);
		if (description == null) {
			throw new BusinessServiceException("Description " + descriptionId + " not found on concept "
					+ concept.getConceptId());
		}
		InactivationIndicator oldIndicator = description.getInactivationIndicator();
		InactivationIndicator newIndicator = requireHistoricalInactivationIndicator(updates);
		List<Map<HistoricalAssociation, Set<String>>> rowTargets = new ArrayList<>();
		for (AcceptedAffectedHistoricalAssociation update : updates) {
			Map<HistoricalAssociation, Set<String>> working = copyAssociationTargets(description.getAssociationTargets());
			removeAssociationTarget(working, inactivatedConceptId);
			applyHistoricalAssociationRow(working, update, null, oldIndicator, true);
			rowTargets.add(working);
		}
		Map<HistoricalAssociation, Set<String>> merged = mergeAssociationTargets(rowTargets);
		description.setInactivationIndicator(newIndicator);
		if (newIndicator != InactivationIndicator.NOT_SEMANTICALLY_EQUIVALENT) {
			// Match getConceptsToUpdate in inactivation.js
			description.setAssociationTargets(null);
		} else {
			description.setAssociationTargets(merged);
		}
	}

	/**
	 * Mirrors updateHistoricalAssociations in inactivation.js.
	 */
	private static void applyHistoricalAssociationRow(Map<HistoricalAssociation, Set<String>> associationTargets,
			AcceptedAffectedHistoricalAssociation update, InactivationIndicator reasonIndicator,
			InactivationIndicator oldIndicator, boolean descriptionToConcept) throws BusinessServiceException {
		if (!StringUtils.hasLength(update.getNewTargetConceptId())) {
			associationTargets.clear();
			return;
		}
		if (!StringUtils.hasLength(update.getAssociationType())) {
			associationTargets.clear();
			return;
		}
		HistoricalAssociation type = resolveAssociationType(update.getAssociationType());
		boolean ambiguousMerge = isAmbiguousMerge(oldIndicator, reasonIndicator);
		if (ambiguousMerge) {
			associationTargets.computeIfAbsent(type, ignored -> new HashSet<>()).add(update.getNewTargetConceptId());
			return;
		}
		if (!descriptionToConcept) {
			associationTargets.clear();
			associationTargets.put(type, new HashSet<>(Set.of(update.getNewTargetConceptId())));
			return;
		}
		Set<String> existing = associationTargets.get(type);
		if (existing != null && !existing.contains(update.getNewTargetConceptId())) {
			existing.add(update.getNewTargetConceptId());
		} else {
			associationTargets.clear();
			associationTargets.put(type, new HashSet<>(Set.of(update.getNewTargetConceptId())));
		}
	}

	private static boolean isAmbiguousMerge(InactivationIndicator oldIndicator, InactivationIndicator reasonIndicator) {
		return reasonIndicator == InactivationIndicator.AMBIGUOUS
				&& oldIndicator == InactivationIndicator.AMBIGUOUS;
	}

	private static InactivationIndicator requireHistoricalInactivationIndicator(
			List<AcceptedAffectedHistoricalAssociation> updates) throws BusinessServiceException {
		for (AcceptedAffectedHistoricalAssociation update : updates) {
			if (update != null && StringUtils.hasLength(update.getInactivationIndicator())) {
				return resolveInactivationIndicator(update.getInactivationIndicator());
			}
		}
		throw new IllegalArgumentException(
				"inactivationIndicator is required for acceptedAffectedHistoricalAssociations.");
	}

	private static DescriptionPojo findDescription(ConceptPojo concept, String descriptionId) {
		if (concept.getDescriptions() == null) {
			return null;
		}
		for (DescriptionPojo description : concept.getDescriptions()) {
			if (description != null && descriptionId.equals(description.getDescriptionId())) {
				return description;
			}
		}
		return null;
	}

	private static Map<HistoricalAssociation, Set<String>> copyAssociationTargets(
			Map<HistoricalAssociation, Set<String>> source) {
		Map<HistoricalAssociation, Set<String>> copy = new EnumMap<>(HistoricalAssociation.class);
		if (source == null) {
			return copy;
		}
		for (Map.Entry<HistoricalAssociation, Set<String>> entry : source.entrySet()) {
			if (entry.getKey() == null) {
				continue;
			}
			copy.put(entry.getKey(), entry.getValue() != null ? new HashSet<>(entry.getValue()) : new HashSet<>());
		}
		return copy;
	}

	private static void removeAssociationTarget(Map<HistoricalAssociation, Set<String>> associationTargets,
			String inactivatedConceptId) {
		if (associationTargets == null || !StringUtils.hasLength(inactivatedConceptId)) {
			return;
		}
		for (Set<String> targets : associationTargets.values()) {
			if (targets != null) {
				targets.remove(inactivatedConceptId);
			}
		}
	}

	private static Map<HistoricalAssociation, Set<String>> mergeAssociationTargets(
			List<Map<HistoricalAssociation, Set<String>>> rowTargets) {
		Map<HistoricalAssociation, Set<String>> merged = new EnumMap<>(HistoricalAssociation.class);
		for (Map<HistoricalAssociation, Set<String>> row : rowTargets) {
			if (row == null) {
				continue;
			}
			for (Map.Entry<HistoricalAssociation, Set<String>> entry : row.entrySet()) {
				if (entry.getKey() == null || CollectionUtils.isEmpty(entry.getValue())) {
					continue;
				}
				merged.computeIfAbsent(entry.getKey(), ignored -> new HashSet<>()).addAll(entry.getValue());
			}
		}
		return merged;
	}

	private static List<ConceptPojo> buildConceptsToUpdate(Set<String> conceptIds,
			Map<String, ConceptPojo> conceptsById, String conceptId, ConceptPojo inactivationConcept) {
		// Preserve a stable order: inactivated concept last (matches UI bulk write).
		List<ConceptPojo> conceptsToUpdate = new ArrayList<>();
		for (String id : conceptIds) {
			if (!id.equals(conceptId) && conceptsById.containsKey(id)) {
				conceptsToUpdate.add(conceptsById.get(id));
			}
		}
		conceptsToUpdate.add(inactivationConcept);
		return conceptsToUpdate;
	}

	private static Set<String> collectRelatedConceptIds(String conceptId, ConceptInactivationRequest request,
			List<AcceptedAffectedHistoricalAssociation> historicalUpdates) {
		Set<String> conceptIds = new LinkedHashSet<>();
		conceptIds.add(conceptId);
		for (AcceptedAffectedConcept accepted : request.getAcceptedAffectedConcepts()) {
			if (accepted != null && StringUtils.hasLength(accepted.getConceptId())) {
				conceptIds.add(accepted.getConceptId());
			}
		}
		for (AcceptedAffectedHistoricalAssociation historical : historicalUpdates) {
			if (historical != null && StringUtils.hasLength(historical.getConceptId())) {
				conceptIds.add(historical.getConceptId());
			}
		}
		return conceptIds;
	}

	private static Map<String, ConceptPojo> indexByConceptId(List<ConceptPojo> concepts) {
		Map<String, ConceptPojo> byId = new HashMap<>();
		if (concepts == null) {
			return byId;
		}
		for (ConceptPojo concept : concepts) {
			if (concept != null && StringUtils.hasLength(concept.getConceptId())) {
				byId.put(concept.getConceptId(), concept);
			}
		}
		return byId;
	}

	private static void applyInactivation(ConceptPojo concept, ConceptInactivationRequest request)
			throws BusinessServiceException {
		concept.setActive(false);
		concept.setInactivationIndicator(resolveInactivationIndicator(request.getReasonId()));
		concept.setAssociationTargets(toAssociationTargets(request.getAssociations()));
	}

	private static void applyAcceptedReplacements(ConceptPojo concept, String inactivatedConceptId,
			List<AcceptedReplacement> replacements) {
		if (CollectionUtils.isEmpty(replacements)) {
			return;
		}
		applyReplacementsToAxioms(concept.getClassAxioms(), inactivatedConceptId, replacements);
		applyReplacementsToAxioms(concept.getGciAxioms(), inactivatedConceptId, replacements);
	}

	private static void applyReplacementsToAxioms(Set<AxiomPojo> axioms, String inactivatedConceptId,
			List<AcceptedReplacement> replacements) {
		if (axioms == null) {
			return;
		}
		for (AxiomPojo axiom : axioms) {
			if (CollectionUtils.isEmpty(axiom.getRelationships())) {
				continue;
			}
			axiom.setRelationships(rebuildRelationships(axiom.getRelationships(), inactivatedConceptId, replacements));
		}
	}

	private static Set<RelationshipPojo> rebuildRelationships(Set<RelationshipPojo> relationships,
			String inactivatedConceptId, List<AcceptedReplacement> replacements) {
		Set<RelationshipPojo> updated = new HashSet<>();
		List<RelationshipPojo> toAdd = new ArrayList<>();
		for (RelationshipPojo relationship : relationships) {
			List<AcceptedReplacement> matching = targetsInactivatedConcept(relationship, inactivatedConceptId)
					? findMatchingReplacements(relationship, replacements)
					: List.of();
			if (matching.isEmpty()) {
				updated.add(relationship);
			} else {
				// Drop the relationship that pointed at the inactivated concept and add replacements.
				for (AcceptedReplacement replacement : matching) {
					toAdd.add(createReplacementRelationship(relationship, replacement.getTargetConceptId()));
				}
			}
		}
		updated.addAll(toAdd);
		return updated;
	}

	private static boolean targetsInactivatedConcept(RelationshipPojo relationship, String inactivatedConceptId) {
		return relationship != null
				&& relationship.getTarget() != null
				&& inactivatedConceptId.equals(relationship.getTarget().getConceptId());
	}

	private static List<AcceptedReplacement> findMatchingReplacements(RelationshipPojo relationship,
			List<AcceptedReplacement> replacements) {
		List<AcceptedReplacement> matching = new ArrayList<>();
		String typeId = relationship.getType() != null ? relationship.getType().getConceptId() : null;
		for (AcceptedReplacement replacement : replacements) {
			if (replacement == null || !StringUtils.hasLength(replacement.getTargetConceptId())) {
				continue;
			}
			if (!StringUtils.hasLength(replacement.getTypeConceptId())
					|| replacement.getTypeConceptId().equals(typeId)) {
				matching.add(replacement);
			}
		}
		return matching;
	}

	private static RelationshipPojo createReplacementRelationship(RelationshipPojo original, String newTargetConceptId) {
		RelationshipPojo replacement = new RelationshipPojo();
		replacement.setActive(true);
		replacement.setReleased(false);
		replacement.setModuleId(original.getModuleId());
		replacement.setSourceId(original.getSourceId());
		replacement.setGroupId(original.getGroupId());
		replacement.setType(original.getType());
		replacement.setCharacteristicType(original.getCharacteristicType());
		replacement.setModifier(original.getModifier());
		replacement.setTarget(new ConceptMiniPojo(newTargetConceptId));
		return replacement;
	}

	private static InactivationIndicator resolveInactivationIndicator(String reasonId) throws BusinessServiceException {
		InactivationIndicator byConceptId = InactivationIndicator.fromConceptId(reasonId);
		if (byConceptId != null) {
			return byConceptId;
		}
		try {
			return InactivationIndicator.valueOf(reasonId);
		} catch (IllegalArgumentException e) {
			throw new BusinessServiceException("Unrecognised inactivation reasonId: " + reasonId, e);
		}
	}

	private static Map<HistoricalAssociation, Set<String>> toAssociationTargets(List<Association> associations)
			throws BusinessServiceException {
		Map<HistoricalAssociation, Set<String>> targets = new EnumMap<>(HistoricalAssociation.class);
		if (CollectionUtils.isEmpty(associations)) {
			return targets;
		}
		for (Association association : associations) {
			if (association == null || !StringUtils.hasLength(association.getType())
					|| !StringUtils.hasLength(association.getTargetConceptId())) {
				continue;
			}
			HistoricalAssociation type = resolveAssociationType(association.getType());
			targets.computeIfAbsent(type, ignored -> new HashSet<>()).add(association.getTargetConceptId());
		}
		return targets;
	}

	private static HistoricalAssociation resolveAssociationType(String type) throws BusinessServiceException {
		HistoricalAssociation byConceptId = HistoricalAssociation.fromConceptId(type);
		if (byConceptId != null) {
			return byConceptId;
		}
		try {
			return HistoricalAssociation.valueOf(type);
		} catch (IllegalArgumentException e) {
			throw new BusinessServiceException("Unrecognised association type: " + type, e);
		}
	}

	private static boolean resolveDryRun(ConceptInactivationRequest request, Boolean dryRunParam) {
		if (dryRunParam != null) {
			return dryRunParam;
		}
		return request.isDryRun();
	}

	private void emitCompletionNotification(String projectKey, String taskKey, String branchPath, String conceptId) {
		Notification notification = new Notification(projectKey, taskKey, EntityType.Inactivation,
				conceptInactivatedMessage(conceptId));
		notification.setBranchPath(branchPath);
		notificationService.queueNotification(SecurityUtil.getUsername(), notification);
	}

	private static String conceptInactivatedMessage(String conceptId) {
		return String.format(CONCEPT_INACTIVATED_MESSAGE, conceptId);
	}
}
