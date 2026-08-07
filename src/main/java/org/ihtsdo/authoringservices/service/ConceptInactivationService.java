package org.ihtsdo.authoringservices.service;

import org.ihtsdo.authoringservices.domain.ConceptInactivationRequest;
import org.ihtsdo.authoringservices.domain.ConceptInactivationRequest.AcceptedAffectedConcept;
import org.ihtsdo.authoringservices.domain.ConceptInactivationRequest.AcceptedReplacement;
import org.ihtsdo.authoringservices.domain.ConceptInactivationRequest.Association;
import org.ihtsdo.authoringservices.domain.CrsBlockingState;
import org.ihtsdo.authoringservices.domain.EntityType;
import org.ihtsdo.authoringservices.domain.Notification;
import org.ihtsdo.authoringservices.service.factory.TaskServiceFactory;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.AxiomPojo;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ConceptMiniPojo;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ConceptPojo;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ConceptPojo.HistoricalAssociation;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ConceptPojo.InactivationIndicator;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.DefinitionStatus;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RelationshipPojo;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ConceptInactivationService {

	static final String CRS_BLOCKED_MESSAGE =
			"Concept inactivation is blocked because this task has unsaved CRS concepts with SCTIDs.";
	private static final String PRIMITIVE_DEFINITION_STATUS_ID = DefinitionStatus.PRIMITIVE.getConceptId();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final PermissionService permissionService;
	private final CrsBlockingStateService crsBlockingStateService;
	private final BranchService branchService;
	private final SnowstormRestClientFactory snowstormRestClientFactory;
	private final NotificationService notificationService;
	private final TaskServiceFactory taskServiceFactory;

	public ConceptInactivationService(PermissionService permissionService,
			CrsBlockingStateService crsBlockingStateService,
			BranchService branchService,
			SnowstormRestClientFactory snowstormRestClientFactory,
			NotificationService notificationService,
			TaskServiceFactory taskServiceFactory) {
		this.permissionService = permissionService;
		this.crsBlockingStateService = crsBlockingStateService;
		this.branchService = branchService;
		this.snowstormRestClientFactory = snowstormRestClientFactory;
		this.notificationService = notificationService;
		this.taskServiceFactory = taskServiceFactory;
	}

	public List<ConceptPojo> inactivate(String projectKey, String taskKey, String conceptId,
			ConceptInactivationRequest request, Boolean dryRunParam) throws BusinessServiceException {
		if (request == null) {
			request = new ConceptInactivationRequest();
		}
		if (!StringUtils.hasLength(conceptId)) {
			throw new IllegalArgumentException("Parameter conceptId is required.");
		}
		if (!StringUtils.hasLength(request.getReasonId())) {
			throw new IllegalArgumentException("reasonId is required.");
		}

		permissionService.checkFullPermissionOnProjectOrThrow(projectKey);

		String username = SecurityUtil.getUsername();
		CrsBlockingState crsBlockingState = crsBlockingStateService.getBlockingState(projectKey, taskKey, username);
		if (crsBlockingState.isBlocked()) {
			throw new BusinessServiceException(CRS_BLOCKED_MESSAGE);
		}

		boolean dryRun = resolveDryRun(request, dryRunParam);
		String branchPath = branchService.getTaskBranchPathUsingCache(projectKey, taskKey);
		SnowstormRestClient client = snowstormRestClientFactory.getClient();

		try {
			List<ConceptPojo> conceptsToUpdate = prepareConceptsForUpdate(client, branchPath, conceptId, request);
			if (dryRun) {
				return conceptsToUpdate;
			}

			List<ConceptPojo> updated = client.bulkUpdateConcepts(branchPath, conceptsToUpdate);
			emitCompletionNotification(projectKey, taskKey, branchPath, conceptId);
			recordActivity(projectKey, taskKey, conceptId);
			return updated;
		} catch (RestClientException e) {
			throw new BusinessServiceException("Failed to inactivate concept " + conceptId, e);
		}
	}

	private List<ConceptPojo> prepareConceptsForUpdate(SnowstormRestClient client, String branchPath,
			String conceptId, ConceptInactivationRequest request) throws RestClientException, BusinessServiceException {
		Set<String> conceptIds = collectRelatedConceptIds(conceptId, request);
		List<ConceptPojo> fetched = client.searchConcepts(branchPath, new ArrayList<>(conceptIds));
		Map<String, ConceptPojo> conceptsById = indexByConceptId(fetched);

		ConceptPojo inactivationConcept = conceptsById.get(conceptId);
		if (inactivationConcept == null) {
			throw new BusinessServiceException("Concept " + conceptId + " not found on branch " + branchPath);
		}
		applyInactivation(inactivationConcept, request);

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

	private static Set<String> collectRelatedConceptIds(String conceptId, ConceptInactivationRequest request) {
		Set<String> conceptIds = new LinkedHashSet<>();
		conceptIds.add(conceptId);
		for (AcceptedAffectedConcept accepted : request.getAcceptedAffectedConcepts()) {
			if (accepted != null && StringUtils.hasLength(accepted.getConceptId())) {
				conceptIds.add(accepted.getConceptId());
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
			if (axiom.getRelationships() == null || axiom.getRelationships().isEmpty()) {
				continue;
			}
			Set<RelationshipPojo> updated = new HashSet<>();
			List<RelationshipPojo> toAdd = new ArrayList<>();
			for (RelationshipPojo relationship : axiom.getRelationships()) {
				if (!targetsInactivatedConcept(relationship, inactivatedConceptId)) {
					updated.add(relationship);
					continue;
				}
				List<AcceptedReplacement> matching = findMatchingReplacements(relationship, replacements);
				if (matching.isEmpty()) {
					updated.add(relationship);
					continue;
				}
				// Drop the relationship that pointed at the inactivated concept and add replacements.
				for (AcceptedReplacement replacement : matching) {
					toAdd.add(createReplacementRelationship(relationship, replacement.getTargetConceptId()));
				}
			}
			updated.addAll(toAdd);
			axiom.setRelationships(updated);
		}
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
		replacement.setRelationshipId(null);
		replacement.setEffectiveTime(null);
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
			throw new BusinessServiceException("Unrecognised inactivation reasonId: " + reasonId);
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
			targets.computeIfAbsent(type, key -> new HashSet<>()).add(association.getTargetConceptId());
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
			throw new BusinessServiceException("Unrecognised association type: " + type);
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
				"Concept " + conceptId + " inactivated");
		notification.setBranchPath(branchPath);
		notificationService.queueNotification(SecurityUtil.getUsername(), notification);
	}

	private void recordActivity(String projectKey, String taskKey, String conceptId) {
		String comment = "Concept " + conceptId + " inactivated";
		try {
			taskServiceFactory.getInstanceByKey(taskKey).addCommentLogErrors(projectKey, taskKey, comment);
		} catch (Exception e) {
			logger.error("Failed to record inactivation activity for task {}/{}: {}", projectKey, taskKey, e.getMessage());
		}
	}
}
