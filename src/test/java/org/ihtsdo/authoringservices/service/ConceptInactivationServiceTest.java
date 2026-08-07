package org.ihtsdo.authoringservices.service;

import org.ihtsdo.authoringservices.domain.ConceptInactivationRequest;
import org.ihtsdo.authoringservices.domain.ConceptInactivationRequest.AcceptedAffectedConcept;
import org.ihtsdo.authoringservices.domain.ConceptInactivationRequest.AcceptedReplacement;
import org.ihtsdo.authoringservices.domain.ConceptInactivationRequest.Association;
import org.ihtsdo.authoringservices.domain.CrsBlockingState;
import org.ihtsdo.authoringservices.domain.CrsBlockingState.BlockingConcept;
import org.ihtsdo.authoringservices.domain.EntityType;
import org.ihtsdo.authoringservices.domain.Notification;
import org.ihtsdo.authoringservices.service.factory.TaskServiceFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.AxiomPojo;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ConceptPojo;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ConceptPojo.HistoricalAssociation;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ConceptPojo.InactivationIndicator;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.DefinitionStatus;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RelationshipPojo;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
	private static final String REPLACEMENT_TARGET = "33333333333";
	private static final String ISA = "116680003";
	private static final String BRANCH = "MAIN/WRPAS/WRPAS-1";
	private static final String AMBIGUOUS_REASON_ID = "900000000000484002";

	@Mock
	private PermissionService permissionService;
	@Mock
	private CrsBlockingStateService crsBlockingStateService;
	@Mock
	private BranchService branchService;
	@Mock
	private SnowstormRestClientFactory snowstormRestClientFactory;
	@Mock
	private NotificationService notificationService;
	@Mock
	private TaskServiceFactory taskServiceFactory;
	@Mock
	private TaskService taskService;
	@Mock
	private SnowstormRestClient snowstormRestClient;

	private ConceptInactivationService service;

	@BeforeEach
	void setUp() {
		service = new ConceptInactivationService(permissionService, crsBlockingStateService, branchService,
				snowstormRestClientFactory, notificationService, taskServiceFactory);
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

		stubUnblockedTaskBranchAndClient();
		when(snowstormRestClient.searchConcepts(eq(BRANCH), anyList()))
				.thenReturn(List.of(inactivationConcept, affectedConcept));
		when(snowstormRestClient.bulkUpdateConcepts(eq(BRANCH), anyList()))
				.thenAnswer(invocation -> invocation.getArgument(1));
		when(taskServiceFactory.getInstanceByKey(TASK)).thenReturn(taskService);

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
		verify(taskService).addCommentLogErrors(PROJECT, TASK, "Concept " + CONCEPT_ID + " inactivated");
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
		verify(taskServiceFactory, never()).getInstanceByKey(any());
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
		when(crsBlockingStateService.getBlockingState(eq(PROJECT), eq(TASK), isNull())).thenReturn(blocked);
		ConceptInactivationRequest request = baseRequest();

		BusinessServiceException exception = assertThrows(BusinessServiceException.class,
				() -> service.inactivate(PROJECT, TASK, CONCEPT_ID, request, false));
		assertEquals(ConceptInactivationService.CRS_BLOCKED_MESSAGE, exception.getMessage());
	}

	private void stubUnblockedTaskBranchAndClient() throws Exception {
		when(crsBlockingStateService.getBlockingState(eq(PROJECT), eq(TASK), isNull())).thenReturn(unblockedState());
		when(branchService.getTaskBranchPathUsingCache(PROJECT, TASK)).thenReturn(BRANCH);
		when(snowstormRestClientFactory.getClient()).thenReturn(snowstormRestClient);
	}

	private static ConceptPojo findConcept(List<ConceptPojo> concepts, String conceptId) {
		return concepts.stream()
				.filter(concept -> conceptId.equals(concept.getConceptId()))
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

	private static CrsBlockingState unblockedState() {
		CrsBlockingState state = new CrsBlockingState();
		state.setBlockingConcepts(List.of());
		return state;
	}
}
