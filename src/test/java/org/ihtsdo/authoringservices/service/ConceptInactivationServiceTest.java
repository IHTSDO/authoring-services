package org.ihtsdo.authoringservices.service;

import org.ihtsdo.authoringservices.domain.ConceptInactivationRequest;
import org.ihtsdo.authoringservices.domain.ConceptInactivationRequest.AcceptedAffectedConcept;
import org.ihtsdo.authoringservices.domain.ConceptInactivationRequest.AcceptedAffectedHistoricalAssociation;
import org.ihtsdo.authoringservices.domain.ConceptInactivationRequest.AcceptedReplacement;
import org.ihtsdo.authoringservices.domain.ConceptInactivationRequest.Association;
import org.ihtsdo.authoringservices.domain.CrsBlockingState;
import org.ihtsdo.authoringservices.domain.CrsBlockingState.BlockingConcept;
import org.ihtsdo.authoringservices.domain.EntityType;
import org.ihtsdo.authoringservices.domain.Notification;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.AxiomPojo;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ConceptPojo;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ConceptPojo.HistoricalAssociation;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ConceptPojo.InactivationIndicator;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.DefinitionStatus;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.DescriptionPojo;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RelationshipPojo;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConceptInactivationServiceTest {

	private static final String PROJECT = "WRPAS";
	private static final String TASK = "WRPAS-1";
	private static final String CONCEPT_ID = "12345678901";
	private static final String AFFECTED_ID = "22222222222";
	private static final String HIST_CONCEPT_ID = "44444444409";
	private static final String HIST_DESC_CONCEPT_ID = "55555555509";
	private static final String HIST_DESCRIPTION_ID = "55555555513";
	private static final String REPLACEMENT_TARGET = "33333333333";
	private static final String ISA = "116680003";
	private static final String BRANCH = "MAIN/WRPAS/WRPAS-1";
	private static final String AMBIGUOUS_REASON_ID = "900000000000484002";

	@Mock
	private PermissionService permissionService;
	@Mock
	private ContentRequestService contentRequestService;
	@Mock
	private BranchService branchService;
	@Mock
	private SnowstormRestClientFactory snowstormRestClientFactory;
	@Mock
	private NotificationService notificationService;
	@Mock
	private SnowstormRestClient snowstormRestClient;

	private ConceptInactivationService service;

	@BeforeEach
	void setUp() {
		service = new ConceptInactivationService(permissionService, contentRequestService, branchService,
				snowstormRestClientFactory, notificationService);
	}

	@Test
	void inactivate_fetchesRelatedConceptsAppliesUpdatesAndBulkWrites() throws Exception {
		ConceptInactivationRequest request = baseRequest();
		AcceptedAffectedConcept accepted = new AcceptedAffectedConcept();
		accepted.setConceptId(AFFECTED_ID);
		AcceptedReplacement replacement = new AcceptedReplacement();
		replacement.setTypeConceptId(ISA);
		replacement.setTargetConceptId(REPLACEMENT_TARGET);
		accepted.setAcceptedReplacements(List.of(replacement));
		request.setAcceptedAffectedConcepts(List.of(accepted));

		ConceptPojo inactivationConcept = activeConcept(CONCEPT_ID);
		ConceptPojo affectedConcept = conceptWithIsaTo(AFFECTED_ID, CONCEPT_ID);
		List<ConceptPojo> concepts = List.of(inactivationConcept, affectedConcept);

		stubUnblockedTaskBranchAndClient();
		// First call loads concepts for update; second call re-fetches after bulk write.
		when(snowstormRestClient.searchConcepts(eq(BRANCH), anyList())).thenReturn(concepts, concepts);
		doNothing().when(snowstormRestClient).bulkUpdateConcepts(eq(BRANCH), anyList());

		List<ConceptPojo> result = service.inactivate(PROJECT, TASK, CONCEPT_ID, request, false);

		assertEquals(2, result.size());
		ConceptPojo inactivated = findConcept(result, CONCEPT_ID);
		assertFalse(inactivated.isActive());
		assertEquals(InactivationIndicator.AMBIGUOUS, inactivated.getInactivationIndicator());
		assertTrue(inactivated.getAssociationTargets().get(HistoricalAssociation.POSSIBLY_EQUIVALENT_TO)
				.contains(REPLACEMENT_TARGET));

		ConceptPojo updatedAffected = findConcept(result, AFFECTED_ID);
		RelationshipPojo newRel = updatedAffected.getClassAxioms().iterator().next().getRelationships().iterator().next();
		assertEquals(REPLACEMENT_TARGET, newRel.getTarget().getConceptId());
		assertFalse(newRel.isReleased());

		ArgumentCaptor<List<ConceptPojo>> conceptsCaptor = ArgumentCaptor.captor();
		verify(snowstormRestClient).bulkUpdateConcepts(eq(BRANCH), conceptsCaptor.capture());
		assertEquals(2, conceptsCaptor.getValue().size());

		ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
		verify(notificationService).queueNotification(isNull(), notificationCaptor.capture());
		assertEquals(EntityType.Inactivation, notificationCaptor.getValue().getEntityType());
	}

	@Test
	void inactivate_dryRunSkipsBulkWriteNotificationAndActivity() throws Exception {
		ConceptInactivationRequest request = baseRequest();
		stubUnblockedTaskBranchAndClient();
		when(snowstormRestClient.searchConcepts(eq(BRANCH), anyList())).thenReturn(List.of(activeConcept(CONCEPT_ID)));

		List<ConceptPojo> result = service.inactivate(PROJECT, TASK, CONCEPT_ID, request, true);

		assertEquals(1, result.size());
		assertFalse(result.get(0).isActive());
		verify(snowstormRestClient, never()).bulkUpdateConcepts(anyString(), anyList());
		verify(notificationService, never()).queueNotification(any(), any());
	}

	@Test
	void inactivate_appliesAcceptedConceptHistoricalAssociations() throws Exception {
		ConceptInactivationRequest request = baseRequest();
		AcceptedAffectedHistoricalAssociation hist = new AcceptedAffectedHistoricalAssociation();
		hist.setConceptId(HIST_CONCEPT_ID);
		hist.setAssociationType("POSSIBLY_EQUIVALENT_TO");
		hist.setNewTargetConceptId(REPLACEMENT_TARGET);
		hist.setInactivationIndicator("DUPLICATE");
		request.setAcceptedAffectedHistoricalAssociations(List.of(hist));

		ConceptPojo inactivationConcept = activeConcept(CONCEPT_ID);
		ConceptPojo histConcept = conceptWithHistoricalAssociation(HIST_CONCEPT_ID, CONCEPT_ID);
		List<ConceptPojo> concepts = List.of(inactivationConcept, histConcept);

		stubUnblockedTaskBranchAndClient();
		when(snowstormRestClient.searchConcepts(eq(BRANCH), anyList())).thenReturn(concepts, concepts);
		doNothing().when(snowstormRestClient).bulkUpdateConcepts(eq(BRANCH), anyList());

		List<ConceptPojo> result = service.inactivate(PROJECT, TASK, CONCEPT_ID, request, false);

		ConceptPojo updated = findConcept(result, HIST_CONCEPT_ID);
		assertEquals(InactivationIndicator.DUPLICATE, updated.getInactivationIndicator());
		assertFalse(updated.getAssociationTargets().get(HistoricalAssociation.POSSIBLY_EQUIVALENT_TO)
				.contains(CONCEPT_ID));
		assertTrue(updated.getAssociationTargets().get(HistoricalAssociation.POSSIBLY_EQUIVALENT_TO)
				.contains(REPLACEMENT_TARGET));
	}

	@Test
	void inactivate_appliesAcceptedDescriptionHistoricalAssociations() throws Exception {
		ConceptInactivationRequest request = baseRequest();
		AcceptedAffectedHistoricalAssociation hist = new AcceptedAffectedHistoricalAssociation();
		hist.setConceptId(HIST_DESC_CONCEPT_ID);
		hist.setDescriptionId(HIST_DESCRIPTION_ID);
		hist.setAssociationType("REFERS_TO");
		hist.setNewTargetConceptId(REPLACEMENT_TARGET);
		hist.setInactivationIndicator("NOT_SEMANTICALLY_EQUIVALENT");
		request.setAcceptedAffectedHistoricalAssociations(List.of(hist));

		ConceptPojo inactivationConcept = activeConcept(CONCEPT_ID);
		ConceptPojo owner = conceptWithDescriptionHistoricalAssociation(
				HIST_DESC_CONCEPT_ID, HIST_DESCRIPTION_ID, CONCEPT_ID, InactivationIndicator.NOT_SEMANTICALLY_EQUIVALENT);
		List<ConceptPojo> concepts = List.of(inactivationConcept, owner);

		stubUnblockedTaskBranchAndClient();
		when(snowstormRestClient.searchConcepts(eq(BRANCH), anyList())).thenReturn(concepts, concepts);
		doNothing().when(snowstormRestClient).bulkUpdateConcepts(eq(BRANCH), anyList());

		List<ConceptPojo> result = service.inactivate(PROJECT, TASK, CONCEPT_ID, request, false);

		DescriptionPojo description = findDescription(findConcept(result, HIST_DESC_CONCEPT_ID), HIST_DESCRIPTION_ID);
		assertEquals(InactivationIndicator.NOT_SEMANTICALLY_EQUIVALENT, description.getInactivationIndicator());
		assertTrue(description.getAssociationTargets().get(HistoricalAssociation.REFERS_TO)
				.contains(REPLACEMENT_TARGET));
		assertFalse(description.getAssociationTargets().get(HistoricalAssociation.REFERS_TO).contains(CONCEPT_ID));
	}

	@Test
	void inactivate_clearsDescriptionAssociationsWhenNotSemanticallyEquivalent() throws Exception {
		ConceptInactivationRequest request = baseRequest();
		AcceptedAffectedHistoricalAssociation hist = new AcceptedAffectedHistoricalAssociation();
		hist.setConceptId(HIST_DESC_CONCEPT_ID);
		hist.setDescriptionId(HIST_DESCRIPTION_ID);
		hist.setInactivationIndicator("AMBIGUOUS");
		request.setAcceptedAffectedHistoricalAssociations(List.of(hist));

		ConceptPojo inactivationConcept = activeConcept(CONCEPT_ID);
		ConceptPojo owner = conceptWithDescriptionHistoricalAssociation(
				HIST_DESC_CONCEPT_ID, HIST_DESCRIPTION_ID, CONCEPT_ID, InactivationIndicator.AMBIGUOUS);
		List<ConceptPojo> concepts = List.of(inactivationConcept, owner);

		stubUnblockedTaskBranchAndClient();
		when(snowstormRestClient.searchConcepts(eq(BRANCH), anyList())).thenReturn(concepts, concepts);
		doNothing().when(snowstormRestClient).bulkUpdateConcepts(eq(BRANCH), anyList());

		List<ConceptPojo> result = service.inactivate(PROJECT, TASK, CONCEPT_ID, request, false);

		DescriptionPojo description = findDescription(findConcept(result, HIST_DESC_CONCEPT_ID), HIST_DESCRIPTION_ID);
		assertNull(description.getAssociationTargets());
	}

	@Test
	void inactivate_throwsWhenPermissionDenied() {
		doThrow(new AccessDeniedException("denied")).when(permissionService)
				.checkFullPermissionOnProjectOrThrow(PROJECT);
		ConceptInactivationRequest request = baseRequest();

		assertThrows(AccessDeniedException.class,
				() -> service.inactivate(PROJECT, TASK, CONCEPT_ID, request, false));
	}

	@Test
	void inactivate_throwsWhenCrsBlocked() {
		CrsBlockingState blocked = new CrsBlockingState();
		blocked.setBlockingConcepts(List.of(new BlockingConcept(CONCEPT_ID, "1", null, "FSN")));
		when(contentRequestService.getBlockingState(eq(PROJECT), eq(TASK), isNull())).thenReturn(blocked);
		ConceptInactivationRequest request = baseRequest();

		BusinessServiceException exception = assertThrows(BusinessServiceException.class,
				() -> service.inactivate(PROJECT, TASK, CONCEPT_ID, request, false));
		assertEquals(ConceptInactivationService.CRS_BLOCKED_MESSAGE, exception.getMessage());
	}

	private void stubUnblockedTaskBranchAndClient() throws BusinessServiceException {
		when(contentRequestService.getBlockingState(eq(PROJECT), eq(TASK), isNull())).thenReturn(unblockedState());
		when(branchService.getTaskBranchPathUsingCache(PROJECT, TASK)).thenReturn(BRANCH);
		when(snowstormRestClientFactory.getClient()).thenReturn(snowstormRestClient);
	}

	private static ConceptPojo findConcept(List<ConceptPojo> concepts, String conceptId) {
		return concepts.stream()
				.filter(concept -> conceptId.equals(concept.getConceptId()))
				.findFirst()
				.orElseThrow();
	}

	private static DescriptionPojo findDescription(ConceptPojo concept, String descriptionId) {
		return concept.getDescriptions().stream()
				.filter(description -> descriptionId.equals(description.getDescriptionId()))
				.findFirst()
				.orElseThrow();
	}

	private static ConceptInactivationRequest baseRequest() {
		ConceptInactivationRequest request = new ConceptInactivationRequest();
		request.setReasonId(AMBIGUOUS_REASON_ID);
		Association association = new Association();
		association.setType("POSSIBLY_EQUIVALENT_TO");
		association.setTargetConceptId(REPLACEMENT_TARGET);
		request.setAssociations(List.of(association));
		return request;
	}

	private static ConceptPojo activeConcept(String conceptId) {
		ConceptPojo concept = new ConceptPojo(conceptId);
		concept.setActive(true);
		concept.setDefinitionStatus(DefinitionStatus.FULLY_DEFINED);
		AxiomPojo axiom = new AxiomPojo();
		axiom.setAxiomId("ax-1");
		axiom.setDefinitionStatusId(DefinitionStatus.FULLY_DEFINED.getConceptId());
		concept.setClassAxioms(Set.of(axiom));
		return concept;
	}

	private static ConceptPojo conceptWithIsaTo(String conceptId, String targetId) {
		ConceptPojo concept = new ConceptPojo(conceptId);
		concept.setActive(true);
		RelationshipPojo relationship = new RelationshipPojo(0, ISA, targetId, "STATED_RELATIONSHIP");
		relationship.setSourceId(conceptId);
		relationship.setReleased(true);
		relationship.setRelationshipId("rel-1");
		AxiomPojo axiom = new AxiomPojo();
		axiom.setAxiomId("ax-2");
		axiom.setRelationships(new HashSet<>(Set.of(relationship)));
		concept.setClassAxioms(Set.of(axiom));
		return concept;
	}

	private static ConceptPojo conceptWithHistoricalAssociation(String conceptId, String targetConceptId) {
		ConceptPojo concept = new ConceptPojo(conceptId);
		concept.setActive(false);
		concept.setInactivationIndicator(InactivationIndicator.AMBIGUOUS);
		Map<HistoricalAssociation, Set<String>> targets = new EnumMap<>(HistoricalAssociation.class);
		targets.put(HistoricalAssociation.POSSIBLY_EQUIVALENT_TO, new HashSet<>(Set.of(targetConceptId)));
		concept.setAssociationTargets(targets);
		return concept;
	}

	private static ConceptPojo conceptWithDescriptionHistoricalAssociation(String conceptId, String descriptionId,
			String targetConceptId, InactivationIndicator indicator) {
		ConceptPojo concept = new ConceptPojo(conceptId);
		concept.setActive(true);
		DescriptionPojo description = new DescriptionPojo();
		description.setDescriptionId(descriptionId);
		description.setConceptId(conceptId);
		description.setActive(false);
		description.setInactivationIndicator(indicator);
		Map<HistoricalAssociation, Set<String>> targets = new EnumMap<>(HistoricalAssociation.class);
		targets.put(HistoricalAssociation.REFERS_TO, new HashSet<>(Set.of(targetConceptId)));
		description.setAssociationTargets(targets);
		concept.setDescriptions(Set.of(description));
		return concept;
	}

	private static CrsBlockingState unblockedState() {
		CrsBlockingState state = new CrsBlockingState();
		state.setBlockingConcepts(List.of());
		return state;
	}
}
