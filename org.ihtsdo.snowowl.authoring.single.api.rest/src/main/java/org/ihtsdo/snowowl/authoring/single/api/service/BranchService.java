package org.ihtsdo.snowowl.authoring.single.api.service;

import static com.google.common.collect.Sets.newHashSet;

import com.b2international.snowowl.api.domain.IComponent;
import com.b2international.snowowl.api.impl.domain.ComponentRef;
import com.b2international.snowowl.core.exceptions.NotFoundException;
import com.b2international.snowowl.datastore.server.branch.Branch;
import com.b2international.snowowl.datastore.server.events.*;
import com.b2international.snowowl.datastore.server.review.ConceptChanges;
import com.b2international.snowowl.datastore.server.review.Review;
import com.b2international.snowowl.datastore.server.review.ReviewStatus;
import com.b2international.snowowl.eventbus.IEventBus;
import com.b2international.snowowl.snomed.api.ISnomedDescriptionService;
import com.b2international.snowowl.snomed.api.domain.DefinitionStatus;
import com.b2international.snowowl.snomed.api.impl.FsnJoinerOperation;
import com.b2international.snowowl.snomed.api.impl.domain.browser.SnomedBrowserRelationshipTarget;
import com.b2international.snowowl.snomed.datastore.SnomedConceptIndexEntry;
import com.b2international.snowowl.snomed.datastore.SnomedRelationshipIndexEntry;
import com.google.common.base.Optional;

import net.rcarz.jiraclient.JiraException;
import org.apache.commons.lang.time.StopWatch;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.ConceptConflict;
import org.ihtsdo.snowowl.authoring.single.api.pojo.ConflictReport;
import org.ihtsdo.snowowl.authoring.single.api.pojo.EntityType;
import org.ihtsdo.snowowl.authoring.single.api.pojo.MergeRequest;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Notification;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.AuthoringTaskReview;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.ChangeType;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.ReviewConcept;
import org.ihtsdo.snowowl.authoring.single.api.service.dao.CdoStore;
import org.ihtsdo.snowowl.authoring.single.api.service.ts.SnomedServiceHelper;
import org.ihtsdo.snowowl.authoring.single.api.service.util.TimerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;

public class BranchService {

	@Autowired
	private SnowOwlBusHelper snowOwlBusHelper;
	
	@Autowired
	private IEventBus eventBus;
	
	@Autowired
	private NotificationService notificationService;

	@Autowired
	private ISnomedDescriptionService descriptionService;
	
	@Autowired
	private CdoStore cdoStore;

	@Autowired
	private TaskService taskService;

	private static final String SNOMED_STORE = "snomedStore";
	private static final String MAIN = "MAIN";
	private static final String CODE_SYSYTEM = "SNOMEDCT";
	public static final String SNOMED_TS_REPOSITORY_ID = "snomedStore";
	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	private static final int REVIEW_TIMEOUT = 60; //Minutes
	private static final int MERGE_TIMEOUT = 60; //Minutes

	public void createTaskBranchAndProjectBranchIfNeeded(String projectKey, String taskKey) throws ServiceException {
		createProjectBranchIfNeeded(projectKey);
		snowOwlBusHelper.makeBusRequest(new CreateBranchEvent(SNOMED_STORE, getBranchPath(projectKey), taskKey, null), BranchReply.class, "Failed to create project branch.", this);
	}

	public Branch.BranchState getBranchState(String project, String taskKey) throws ServiceException {
		final String branchPath = PathHelper.getPath(project, taskKey);
		final BranchReply branchReply = snowOwlBusHelper.makeBusRequest(new ReadBranchEvent(SNOMED_STORE, branchPath), BranchReply.class, "Failed to read branch " + branchPath, this);
		return branchReply.getBranch().state();
	}

	public Branch.BranchState getBranchStateNoThrow(String projectKey, String issueKey) {
		try {
			return getBranchState(projectKey, issueKey);
		} catch (ServiceException e) {
			return null;
		}
	}

	public AuthoringTaskReview diffTaskAgainstProject(String projectKey, String taskKey, List<Locale> locales) throws ExecutionException, InterruptedException {
		return doDiff(getTaskPath(projectKey, taskKey), getProjectPath(projectKey), locales);
	}

	public AuthoringTaskReview diffProjectAgainstMain(String projectKey, List<Locale> locales) throws ExecutionException, InterruptedException {
		return doDiff(getProjectPath(projectKey), MAIN, locales);
	}
	
	public Branch rebaseTask(String projectKey, String taskKey, MergeRequest mergeRequest, String username) throws BusinessServiceException {
		StopWatch stopwatch = new StopWatch();
		stopwatch.start();
		Branch branch = mergeBranch(getProjectPath(projectKey), getTaskPath(projectKey, taskKey), mergeRequest.getSourceReviewId(), username);
		stopwatch.stop();
		String resultMessage = "Rebase from project to " + taskKey +  " completed without conflicts in " + stopwatch;
		notificationService.queueNotification(username, new Notification(projectKey, taskKey, EntityType.Rebase, resultMessage));
		return branch;
	}
	
	public Branch promoteTask(String projectKey, String taskKey, MergeRequest mergeRequest, String username) throws BusinessServiceException, JiraException {
		StopWatch stopwatch = new StopWatch();
		stopwatch.start();
		Branch branch = mergeBranch(getTaskPath(projectKey, taskKey), getProjectPath(projectKey), mergeRequest.getSourceReviewId(), username);
		stopwatch.stop();
		taskService.stateTransition(projectKey, taskKey, TaskStatus.PROMOTED);
		String resultMessage = "Promotion of " + taskKey + " completed without conflicts in " + stopwatch;
		notificationService.queueNotification(username, new Notification(projectKey, taskKey, EntityType.Promotion, resultMessage));
		return branch;
	}
	
	public AuthoringTaskReview diffProjectAgainstTask(String projectKey, String taskKey, List<Locale> locales) throws ExecutionException, InterruptedException {
		return doDiff(getProjectPath(projectKey), getTaskPath(projectKey, taskKey), locales);
	}

	public ReviewStatus getReviewStatus(String id) throws ExecutionException, InterruptedException {
		return getReview(id).status();
	}

	private AuthoringTaskReview doDiff(String sourcePath, String targetPath, List<Locale> locales) throws ExecutionException, InterruptedException {
		final TimerUtil timer = new TimerUtil("Review");
		final AuthoringTaskReview review = new AuthoringTaskReview();
		logger.info("Creating TS review - source {}, target {}", sourcePath, targetPath);
		final ReviewReply reviewReply = new CreateReviewEvent(SNOMED_TS_REPOSITORY_ID, sourcePath, targetPath)
				.send(eventBus, ReviewReply.class).get();
		timer.checkpoint("request review");

		Review tsReview = reviewReply.getReview();
		logger.info("Waiting for TS review to complete");
		for (int a = 0; a < REVIEW_TIMEOUT; a++) {
			tsReview = getReview(tsReview.id());
			if (!ReviewStatus.PENDING.equals(tsReview.status())) {
				break;
			}
			Thread.sleep(1000);
		}
		timer.checkpoint("waiting for review to finish");
		logger.info("TS review {} status {}", tsReview.id(), tsReview.status());
		review.setReviewId(tsReview.id());

		final ConceptChangesReply conceptChangesReply = new ReadConceptChangesEvent(SNOMED_TS_REPOSITORY_ID, tsReview.id())
				.send(eventBus, ConceptChangesReply.class).get();
		timer.checkpoint("getting changes");

		final ConceptChanges conceptChanges = conceptChangesReply.getConceptChanges();
		addAllToReview(review, ChangeType.created, conceptChanges.newConcepts(), sourcePath, locales);
		addAllToReview(review, ChangeType.modified, conceptChanges.changedConcepts(), sourcePath, locales);
		addAllToReview(review, ChangeType.deleted, conceptChanges.deletedConcepts(), sourcePath, locales);
		timer.checkpoint("building review with terms");
		timer.finish();
		logger.info("Review {} built", tsReview.id());
		return review;
	}

	private Review getReview(String id) throws InterruptedException, ExecutionException {
		final ReviewReply latestReviewReply = new ReadReviewEvent(SNOMED_TS_REPOSITORY_ID, id).send(eventBus, ReviewReply.class).get();
		if (latestReviewReply == null) {
			throw new NotFoundException("Review", id);
		}
		return latestReviewReply.getReview();
	}

	private void addAllToReview(AuthoringTaskReview review, ChangeType changeType, Set<String> conceptIds, String branchPath, List<Locale> locales) {
		for (String conceptId : conceptIds) {
			final String term = descriptionService.getFullySpecifiedName(SnomedServiceHelper.createComponentRef(branchPath, conceptId), locales).getTerm();
			review.addConcept(new ReviewConcept(conceptId, term, changeType));
		}
	}

	private void createProjectBranchIfNeeded(String projectKey) throws ServiceException {
		try {
			snowOwlBusHelper.makeBusRequest(new ReadBranchEvent(SNOMED_STORE, getBranchPath(projectKey)), BranchReply.class, "Failed to find project branch.", this);
		} catch (ServiceException e) {
			snowOwlBusHelper.makeBusRequest(new CreateBranchEvent(SNOMED_STORE, MAIN, projectKey, null), BranchReply.class, "Failed to create project branch.", this);
		}
	}

	private String getBranchPath(String projectKey) {
		return MAIN + "/" + projectKey;
	}

	public String getTaskPath(String projectKey, String taskKey) {
		return getBranchPath(projectKey + "/" + taskKey);
	}

	public String getProjectPath(String projectKey) {
		return getBranchPath(projectKey);
	}
	
	private Branch mergeBranch(String sourcePath, String targetPath, String reviewId, String username) throws BusinessServiceException {
		try {
			String commitMsg = username + " performed merge of " + sourcePath + " to " + targetPath;
			MergeEvent mergeEvent = new MergeEvent( SNOMED_TS_REPOSITORY_ID, sourcePath, targetPath, commitMsg, reviewId);
			BranchReply branchReply = mergeEvent.send(eventBus, BranchReply.class).get(MERGE_TIMEOUT, TimeUnit.MINUTES);
			return branchReply.getBranch();
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			throw new BusinessServiceException ("Failure while attempting to merge " + sourcePath + " to " + targetPath + " due to " + e.getMessage(), e );
		} 
	}

	public ConflictReport createConflictReport(String projectKey, String taskKey, List<Locale> locales) throws BusinessServiceException {
		return doCreateConflictReport(getProjectPath(projectKey), getTaskPath(projectKey, taskKey), locales);
	}

	private ConflictReport doCreateConflictReport(String sourcePath,
			String targetPath, List<Locale> locales) throws BusinessServiceException {
		try {
			//Get changes in the target compared to the source, plus changes in the source
			//compared to the target and determine which ones intersect
			ExecutorService executor = Executors.newFixedThreadPool(2);
			AuthoringTaskReviewRunner targetChangesReviewRunner = new AuthoringTaskReviewRunner(targetPath, sourcePath, locales);
			AuthoringTaskReviewRunner sourceChangesReviewRunner = new AuthoringTaskReviewRunner(sourcePath, targetPath, locales);
			
			Future<AuthoringTaskReview> targetChangesReview = executor.submit(targetChangesReviewRunner);
			Future<AuthoringTaskReview> sourceChangesReview = executor.submit(sourceChangesReviewRunner);
			
			//Wait for both of these to complete
			executor.shutdown();
			executor.awaitTermination(REVIEW_TIMEOUT, TimeUnit.MINUTES);

			//Form Set of source changes so as to avoid n x m iterations
			Set<String> sourceChanges = new HashSet<>();
			for (ReviewConcept thisConcept : sourceChangesReview.get().getConcepts()) {
				sourceChanges.add(thisConcept.getId());
			}
			
			List<ConceptConflict> conflictingConcepts = new ArrayList<>();
			//Work through Target Changes to find concepts in common
			for (ReviewConcept thisConcept : targetChangesReview.get().getConcepts()) {
				if (sourceChanges.contains(thisConcept.getId())) {
					conflictingConcepts.add(new ConceptConflict(thisConcept.getId()));
				}
			}
			
			populateLastUpdateTimes(sourcePath, targetPath, conflictingConcepts);
			conflictingConcepts = populateFSNs(conflictingConcepts, locales, targetPath);
			
			ConflictReport conflictReport = new ConflictReport();
			conflictReport.setConcepts(conflictingConcepts);
			conflictReport.setTargetReviewId(targetChangesReview.get().getReviewId());
			conflictReport.setSourceReviewId(sourceChangesReview.get().getReviewId());
			return conflictReport;
		} catch (ExecutionException|InterruptedException|SQLException e) {
			throw new BusinessServiceException ("Unable to retrieve Conflict report for " + targetPath + " due to " + e.getMessage(), e);
		}
	}
	
	private List<ConceptConflict> populateFSNs(final List<ConceptConflict> conflictingConcepts, List<Locale> locales, String targetBranchPath) {

		List<ConceptConflict> results;
		if (conflictingConcepts == null || conflictingConcepts.size() == 0) {
			results = conflictingConcepts;
		} else {
			StopWatch stopwatch = new StopWatch();
			stopwatch.start();
			ComponentRef exampleConcept  = new ComponentRef(CODE_SYSYTEM, targetBranchPath, conflictingConcepts.get(0).getId());
			results = new FsnJoinerOperation<ConceptConflict>(exampleConcept, locales, descriptionService) {
	
				@Override
				protected Collection<SnomedConceptIndexEntry> getConceptEntries(String conceptId) {
					final Set<String> conflictingConceptIds = newHashSet();
					for (final ConceptConflict conflictingConcept : conflictingConcepts) {
						conflictingConceptIds.add(conflictingConcept.getId());
					}
					return getTerminologyBrowser().getConcepts(branchPath, conflictingConceptIds);
				}
	
				@Override
				protected ConceptConflict convertConceptEntry(SnomedConceptIndexEntry conflictingConceptIdx, Optional<String> optionalFsn) {
					final ConceptConflict conflictingConcept = new ConceptConflict(conflictingConceptIdx.getId());
					conflictingConcept.setFsn(optionalFsn.or("[Unable to recover FSN]"));
					return conflictingConcept;
				}
			}.run();
			stopwatch.stop();
			logger.info ("Populated " + results.size() + " fsns in " + stopwatch);
		}
		return results;
	}

	private void populateLastUpdateTimes(String sourcePath, String targetPath,
			List<ConceptConflict> conflictingConcepts) throws SQLException {
		
		//No conflicts, no work to do!
		if ( conflictingConcepts == null || conflictingConcepts.size() == 0) {
			return;
		}
		
		//We need these last updated times from both the source and the targetPath
		BranchPath sourceBranchPath = new BranchPath(sourcePath);
		BranchPath targetBranchPath = new BranchPath(targetPath);
		
		Integer sourceBranchId = cdoStore.getBranchId(sourceBranchPath.getParentName(), sourceBranchPath.getChildName());
		Integer targetBranchId = cdoStore.getBranchId(targetBranchPath.getParentName(), targetBranchPath.getChildName());
		List<IComponent> concepts = new ArrayList<IComponent>(conflictingConcepts);
		
		Map<String, Date> sourceLastUpdatedMap = cdoStore.getLastUpdated(sourceBranchId, concepts);
		Map<String, Date> targetLastUpdatedMap = cdoStore.getLastUpdated(targetBranchId, concepts);
		
		//Now loop through our conflicting concepts and populate those last updated times, if known
		logger.info ("Populating " + conflictingConcepts.size() + " conflicts with " + sourceLastUpdatedMap.size() + " source times and "
				+ targetLastUpdatedMap.size() + " target times");
		for(ConceptConflict thisConflict : conflictingConcepts) {
			if (sourceLastUpdatedMap.containsKey(thisConflict.getId())) {
				thisConflict.setSourceLastUpdate(sourceLastUpdatedMap.get(thisConflict.getId()));
			}
			
			if (targetLastUpdatedMap.containsKey(thisConflict.getId())) {
				thisConflict.setTargetLastUpdate(targetLastUpdatedMap.get(thisConflict.getId()));
			}
		}
		
	}

	class AuthoringTaskReviewRunner implements Callable< AuthoringTaskReview> {
		
		final String sourcePath;
		final String targetPath;
		final List<Locale> locales;
		
		AuthoringTaskReviewRunner(String sourcePath, String targetPath, List<Locale> locales) {
			this.sourcePath = sourcePath;
			this.targetPath = targetPath;
			this.locales = locales;
		}

		@Override
		public AuthoringTaskReview call() throws Exception {
			return doDiff(sourcePath, targetPath, locales);
		}
		
	}

	public ConflictReport createConflictReport(String projectKey, List<Locale> locales) throws BusinessServiceException {
		return doCreateConflictReport(MAIN, getProjectPath(projectKey), locales);
	}

	public void rebaseProject(String projectKey, MergeRequest mergeRequest,
			String username) throws BusinessServiceException {
		StopWatch stopwatch = new StopWatch();
		stopwatch.start();
		mergeBranch (MAIN, getProjectPath(projectKey), mergeRequest.getSourceReviewId(), username);
		stopwatch.stop();
		String resultMessage = "Rebase from MAIN to " + projectKey + " completed without conflicts in " + stopwatch;
		notificationService.queueNotification(username, new Notification(projectKey, EntityType.Rebase, resultMessage));
	}

	public void promoteProject(String projectKey,
			MergeRequest mergeRequest, String username) throws BusinessServiceException {
		StopWatch stopwatch = new StopWatch();
		stopwatch.start();
		mergeBranch (getProjectPath(projectKey), MAIN, mergeRequest.getSourceReviewId(), username);
		stopwatch.stop();
		String resultMessage = "Promotion of " + projectKey + " to MAIN completed without conflicts in " + stopwatch;
		notificationService.queueNotification(username, new Notification(projectKey, EntityType.Promotion, resultMessage));
	}

	private class BranchPath {
		String [] pathElements;
		BranchPath (String path) {
			this.pathElements = path.split("/");
		}
		
		//Child is the last string in the path
		String getChildName() {
			return pathElements[pathElements.length - 1];
		}
		
		//Parent is the String before the last slash
		String getParentName() {
			return pathElements.length > 1 ? pathElements[pathElements.length - 2] : null;
		}
	}
}
