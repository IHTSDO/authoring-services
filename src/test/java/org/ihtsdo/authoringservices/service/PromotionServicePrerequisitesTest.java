package org.ihtsdo.authoringservices.service;

import org.ihtsdo.authoringservices.domain.AuthoringTask;
import org.ihtsdo.authoringservices.domain.BranchState;
import org.ihtsdo.authoringservices.domain.CrsBlockingState.BlockingConcept;
import org.ihtsdo.authoringservices.domain.PromotionPrerequisites;
import org.ihtsdo.authoringservices.domain.TaskStatus;
import org.ihtsdo.authoringservices.service.ClassificationPrerequisiteService.ClassificationPrerequisiteResult;
import org.ihtsdo.authoringservices.service.ClassificationPrerequisiteService.Context;
import org.ihtsdo.authoringservices.service.client.AuthoringAcceptanceGatewayClient;
import org.ihtsdo.authoringservices.service.client.TraceabilityClient;
import org.ihtsdo.authoringservices.service.factory.TaskServiceFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Branch;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Classification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionServicePrerequisitesTest {

	private static final String PROJECT = "WRPAS";
	private static final String TASK = "WRPAS-1";
	private static final String USER = "author";
	private static final String BRANCH = "MAIN/WRPAS/WRPAS-1";

	@Mock
	private TaskServiceFactory taskServiceFactory;
	@Mock
	private TaskService taskService;
	@Mock
	private BranchService branchService;
	@Mock
	private ClassificationPrerequisiteService classificationPrerequisiteService;
	@Mock
	private CrsBlockingStateService crsBlockingStateService;
	@Mock
	private AuthoringAcceptanceGatewayClient aagClient;
	@Mock
	private Branch branch;
	@Mock
	private Classification classification;

	@InjectMocks
	private PromotionService promotionService;

	@Test
	void getPromotionPrerequisites_promotableWhenForwardWithCurrentClassificationAndSacSignedOff() throws Exception {
		AuthoringTask task = task(TaskStatus.REVIEW_COMPLETED, BranchState.FORWARD.name());
		stubTask(task);
		when(branchService.getBranchOrNull(BRANCH)).thenReturn(branch);
		stubEmptyActivities();
		when(classificationPrerequisiteService.getLatestClassificationOrNull(BRANCH)).thenReturn(classification);
		when(classificationPrerequisiteService.evaluate(eq(branch), eq(classification), any(), eq(Context.PROMOTION)))
				.thenReturn(new ClassificationPrerequisiteResult(true, "COMPLETED", false, List.of()));
		when(crsBlockingStateService.collectBlockingConcepts(PROJECT, TASK, USER)).thenReturn(List.of());
		when(crsBlockingStateService.formatBlockingConcepts(List.of())).thenReturn(List.of());
		when(aagClient.areTaskSacSignedOff(BRANCH)).thenReturn(true);

		PromotionPrerequisites result = promotionService.getPromotionPrerequisites(PROJECT, TASK, USER);

		assertTrue(result.isPromotable());
		assertTrue(result.isClassificationCurrent());
		assertEquals("COMPLETED", result.getClassificationStatus());
		assertFalse(result.isEquivalenciesFound());
		assertEquals("Review Completed", result.getReviewStatus());
		assertEquals(BranchState.FORWARD.name(), result.getBranchState());
		assertTrue(result.isSacSignedOff());
		assertTrue(result.getBlockers().isEmpty());
		assertTrue(result.getCrsBlockingConcepts().isEmpty());
	}

	@Test
	void getPromotionPrerequisites_blocksWhenDiverged() throws Exception {
		AuthoringTask task = task(TaskStatus.REVIEW_COMPLETED, BranchState.DIVERGED.name());
		stubTask(task);
		when(aagClient.areTaskSacSignedOff(BRANCH)).thenReturn(true);
		when(crsBlockingStateService.collectBlockingConcepts(PROJECT, TASK, USER)).thenReturn(List.of());
		when(crsBlockingStateService.formatBlockingConcepts(List.of())).thenReturn(List.of());

		PromotionPrerequisites result = promotionService.getPromotionPrerequisites(PROJECT, TASK, USER);

		assertFalse(result.isPromotable());
		assertEquals(BranchState.DIVERGED.name(), result.getBranchState());
		assertTrue(result.getBlockers().stream().anyMatch(b -> b.startsWith("Task and Project Diverged")));
	}

	@Test
	void getPromotionPrerequisites_blocksWhenUpToDate() throws Exception {
		AuthoringTask task = task(TaskStatus.REVIEW_COMPLETED, BranchState.UP_TO_DATE.name());
		stubTask(task);
		when(aagClient.areTaskSacSignedOff(BRANCH)).thenReturn(true);
		when(crsBlockingStateService.collectBlockingConcepts(PROJECT, TASK, USER)).thenReturn(List.of());
		when(crsBlockingStateService.formatBlockingConcepts(List.of())).thenReturn(List.of());

		PromotionPrerequisites result = promotionService.getPromotionPrerequisites(PROJECT, TASK, USER);

		assertFalse(result.isPromotable());
		assertTrue(result.getBlockers().stream().anyMatch(b -> b.startsWith("No Changes To Promote")));
	}

	@Test
	void getPromotionPrerequisites_blocksWhenEquivalenciesFound() throws Exception {
		AuthoringTask task = task(TaskStatus.REVIEW_COMPLETED, BranchState.FORWARD.name());
		stubTask(task);
		when(branchService.getBranchOrNull(BRANCH)).thenReturn(branch);
		stubEmptyActivities();
		when(classificationPrerequisiteService.getLatestClassificationOrNull(BRANCH)).thenReturn(classification);
		when(classificationPrerequisiteService.evaluate(eq(branch), eq(classification), any(), eq(Context.PROMOTION)))
				.thenReturn(new ClassificationPrerequisiteResult(true, "COMPLETED", true,
						List.of("Equivalencies Found: Classification reports equivalent concepts on this branch. You may not promote until these are resolved")));
		when(crsBlockingStateService.collectBlockingConcepts(PROJECT, TASK, USER)).thenReturn(List.of());
		when(crsBlockingStateService.formatBlockingConcepts(List.of())).thenReturn(List.of());
		when(aagClient.areTaskSacSignedOff(BRANCH)).thenReturn(true);

		PromotionPrerequisites result = promotionService.getPromotionPrerequisites(PROJECT, TASK, USER);

		assertFalse(result.isPromotable());
		assertTrue(result.isEquivalenciesFound());
		assertTrue(result.getBlockers().stream().anyMatch(b -> b.startsWith("Equivalencies Found")));
	}

	@Test
	void getPromotionPrerequisites_marksClassificationStaleWhenNotCurrent() throws Exception {
		AuthoringTask task = task(TaskStatus.REVIEW_COMPLETED, BranchState.FORWARD.name());
		stubTask(task);
		when(branchService.getBranchOrNull(BRANCH)).thenReturn(branch);
		stubEmptyActivities();
		when(classificationPrerequisiteService.getLatestClassificationOrNull(BRANCH)).thenReturn(classification);
		when(classificationPrerequisiteService.evaluate(eq(branch), eq(classification), any(), eq(Context.PROMOTION)))
				.thenReturn(new ClassificationPrerequisiteResult(false, "STALE", false,
						List.of("Classification Not Current: Classification was run, but modifications were made after the classifier was initiated.")));
		when(crsBlockingStateService.collectBlockingConcepts(PROJECT, TASK, USER)).thenReturn(List.of());
		when(crsBlockingStateService.formatBlockingConcepts(List.of())).thenReturn(List.of());
		when(aagClient.areTaskSacSignedOff(BRANCH)).thenReturn(true);

		PromotionPrerequisites result = promotionService.getPromotionPrerequisites(PROJECT, TASK, USER);

		assertTrue(result.isPromotable());
		assertFalse(result.isClassificationCurrent());
		assertEquals("STALE", result.getClassificationStatus());
		assertTrue(result.getBlockers().stream().anyMatch(b -> b.startsWith("Classification Not Current")));
	}

	@Test
	void getPromotionPrerequisites_blocksWhenSacNotSignedOff() throws Exception {
		AuthoringTask task = task(TaskStatus.REVIEW_COMPLETED, BranchState.FORWARD.name());
		stubTask(task);
		when(branchService.getBranchOrNull(BRANCH)).thenReturn(branch);
		stubEmptyActivities();
		when(classificationPrerequisiteService.getLatestClassificationOrNull(BRANCH)).thenReturn(classification);
		when(classificationPrerequisiteService.evaluate(eq(branch), eq(classification), any(), eq(Context.PROMOTION)))
				.thenReturn(new ClassificationPrerequisiteResult(true, "COMPLETED", false, List.of()));
		when(crsBlockingStateService.collectBlockingConcepts(PROJECT, TASK, USER)).thenReturn(List.of());
		when(crsBlockingStateService.formatBlockingConcepts(List.of())).thenReturn(List.of());
		when(aagClient.areTaskSacSignedOff(BRANCH)).thenReturn(false);

		PromotionPrerequisites result = promotionService.getPromotionPrerequisites(PROJECT, TASK, USER);

		assertFalse(result.isPromotable());
		assertFalse(result.isSacSignedOff());
		assertTrue(result.getBlockers().contains("Not all Acceptance Criteria have been signed off"));
	}

	@Test
	void getPromotionPrerequisites_includesCrsBlockingConceptsAsSoftWarning() throws Exception {
		AuthoringTask task = task(TaskStatus.REVIEW_COMPLETED, BranchState.FORWARD.name());
		stubTask(task);
		when(branchService.getBranchOrNull(BRANCH)).thenReturn(branch);
		stubEmptyActivities();
		when(classificationPrerequisiteService.getLatestClassificationOrNull(BRANCH)).thenReturn(classification);
		when(classificationPrerequisiteService.evaluate(eq(branch), eq(classification), any(), eq(Context.PROMOTION)))
				.thenReturn(new ClassificationPrerequisiteResult(true, "COMPLETED", false, List.of()));
		List<BlockingConcept> blocking = List.of(new BlockingConcept("12345678901", "99", null, "Pneumonia"));
		when(crsBlockingStateService.collectBlockingConcepts(PROJECT, TASK, USER)).thenReturn(blocking);
		when(crsBlockingStateService.formatBlockingConcepts(blocking))
				.thenReturn(List.of("12345678901 (Request ID: 99)"));
		when(aagClient.areTaskSacSignedOff(BRANCH)).thenReturn(true);

		PromotionPrerequisites result = promotionService.getPromotionPrerequisites(PROJECT, TASK, USER);

		assertTrue(result.isPromotable());
		assertEquals(List.of("12345678901 (Request ID: 99)"), result.getCrsBlockingConcepts());
		assertTrue(result.getBlockers().stream().anyMatch(b -> b.contains("12345678901")));
	}

	@Test
	void getPromotionPrerequisites_softWarnsWhenReviewNotCompletedButStillPromotable() throws Exception {
		AuthoringTask task = task(TaskStatus.IN_PROGRESS, BranchState.FORWARD.name());
		stubTask(task);
		when(branchService.getBranchOrNull(BRANCH)).thenReturn(branch);
		stubEmptyActivities();
		when(classificationPrerequisiteService.getLatestClassificationOrNull(BRANCH)).thenReturn(classification);
		when(classificationPrerequisiteService.evaluate(eq(branch), eq(classification), any(), eq(Context.PROMOTION)))
				.thenReturn(new ClassificationPrerequisiteResult(true, "COMPLETED", false, List.of()));
		when(crsBlockingStateService.collectBlockingConcepts(PROJECT, TASK, USER)).thenReturn(List.of());
		when(crsBlockingStateService.formatBlockingConcepts(List.of())).thenReturn(List.of());
		when(aagClient.areTaskSacSignedOff(BRANCH)).thenReturn(true);

		PromotionPrerequisites result = promotionService.getPromotionPrerequisites(PROJECT, TASK, USER);

		assertTrue(result.isPromotable());
		assertEquals("In Progress", result.getReviewStatus());
		assertTrue(result.getBlockers().stream().anyMatch(b -> b.startsWith("No review completed")));
	}

	private void stubTask(AuthoringTask task) throws Exception {
		when(taskServiceFactory.getInstanceByKey(TASK)).thenReturn(taskService);
		when(taskService.retrieveTask(eq(PROJECT), eq(TASK), anyBoolean(), anyBoolean())).thenReturn(task);
		when(branchService.getTaskBranchPathUsingCache(PROJECT, TASK)).thenReturn(BRANCH);
	}

	private void stubEmptyActivities() {
		TraceabilityClient.ActivitiesPage page = new TraceabilityClient.ActivitiesPage();
		page.setContent(List.of());
		page.setNumberOfElements(0);
		when(classificationPrerequisiteService.fetchActivities(BRANCH)).thenReturn(page);
	}

	private static AuthoringTask task(TaskStatus status, String branchState) {
		AuthoringTask authoringTask = new AuthoringTask();
		authoringTask.setKey(TASK);
		authoringTask.setProjectKey(PROJECT);
		authoringTask.setStatus(status);
		authoringTask.setBranchState(branchState);
		return authoringTask;
	}
}
