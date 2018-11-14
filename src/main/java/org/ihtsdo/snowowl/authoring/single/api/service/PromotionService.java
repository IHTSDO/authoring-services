package org.ihtsdo.snowowl.authoring.single.api.service;

import static org.ihtsdo.otf.rest.client.snowowl.pojo.MergeReviewsResults.MergeReviewStatus.CURRENT;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import javax.annotation.PreDestroy;

import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.snowowl.PathHelper;
import org.ihtsdo.otf.rest.client.snowowl.SnowOwlRestClient;
import org.ihtsdo.otf.rest.client.snowowl.SnowOwlRestClientFactory;
import org.ihtsdo.otf.rest.client.snowowl.pojo.ApiError;
import org.ihtsdo.otf.rest.client.snowowl.pojo.ClassificationResults;
import org.ihtsdo.otf.rest.client.snowowl.pojo.Merge;
import org.ihtsdo.otf.rest.client.snowowl.pojo.MergeReviewsResults;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTask;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AutomatePromoteProcess;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Classification;
import org.ihtsdo.snowowl.authoring.single.api.pojo.EntityType;
import org.ihtsdo.snowowl.authoring.single.api.pojo.MergeRequest;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Notification;
import org.ihtsdo.snowowl.authoring.single.api.pojo.ProcessStatus;
import org.ihtsdo.snowowl.authoring.single.api.rest.ControllerHelper;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.BranchState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.rcarz.jiraclient.Issue;
import us.monoid.json.JSONException;

@Service
public class PromotionService {

	@Autowired
	private TaskService taskService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private SnowOwlRestClientFactory snowOwlRestClientFactory;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ClassificationService classificationService;

	private final Map<String, ProcessStatus> automateTaskPromotionStatus;
	
	private final Map<String, ProcessStatus> taskPromotionStatus;
	
	private final Map<String, ProcessStatus> projectPromotionStatus;

	private final ExecutorService executorService;

	private final LinkedBlockingQueue<AutomatePromoteProcess> autoPromoteBlockingQueue = new LinkedBlockingQueue<AutomatePromoteProcess>();
	
	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	public PromotionService() {
		automateTaskPromotionStatus = new HashMap<>();
		taskPromotionStatus = new HashMap<>();
		projectPromotionStatus = new HashMap<>();
		executorService = Executors.newCachedThreadPool();
	}

	@Scheduled(initialDelay = 60_000, fixedDelay = 5_000)
	private void processAutoPromotionJobs() {
		try {
			AutomatePromoteProcess automatePromoteProcess = autoPromoteBlockingQueue.take();
			doAutomateTaskPromotion(automatePromoteProcess.getProjectKey(), automatePromoteProcess.getTaskKey(), automatePromoteProcess.getAuthentication());
		} catch (InterruptedException e) {
			logger.warn("Failed to take task auto-promotion job from the queue.", e);
		}
	}
	
	public void queueAutomateTaskPromotion(String projectKey, String taskKey) throws BusinessServiceException {
		AutomatePromoteProcess automatePromoteProcess = new AutomatePromoteProcess(SecurityContextHolder.getContext().getAuthentication(), projectKey, taskKey);
		
		try {
			ProcessStatus processStatus = new ProcessStatus("Queued","");
			automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), processStatus);
			
			autoPromoteBlockingQueue.put(automatePromoteProcess);
		} catch (InterruptedException e) {
			ProcessStatus failedStatus = new ProcessStatus("Failed", e.getMessage());
			automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), failedStatus);
		}		
	}

	public void doTaskPromotion(String projectKey, String taskKey, MergeRequest mergeRequest) throws BusinessServiceException {
		String taskBranchPath = taskService.getTaskBranchPathUsingCache(projectKey, taskKey);
		final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		ProcessStatus taskProcessStatus = new ProcessStatus();
		executorService.submit(() -> {
			SecurityContextHolder.getContext().setAuthentication(authentication);			
			try {
				
				taskProcessStatus.setStatus("Rebasing");
				taskProcessStatus.setMessage("Task is rebasing");
				taskPromotionStatus.put(parseKey(projectKey, taskKey), taskProcessStatus);
				Merge merge = branchService.mergeBranchSync(taskBranchPath, PathHelper.getParentPath(taskBranchPath), mergeRequest.getSourceReviewId());
				if (merge.getStatus() == Merge.Status.COMPLETED) {
					taskService.stateTransition(projectKey, taskKey, TaskStatus.PROMOTED);
					notificationService.queueNotification(ControllerHelper.getUsername(),
							new Notification(projectKey, taskKey, EntityType.Promotion, "Task successfully promoted"));
					taskProcessStatus.setStatus("Promotion Complete");
					taskProcessStatus.setMessage("Task successfully promoted");
					taskPromotionStatus.put(parseKey(projectKey, taskKey), taskProcessStatus);
				} else if (merge.getStatus().equals(Merge.Status.CONFLICTS)) {
					try {
						ObjectMapper mapper = new ObjectMapper();
						String jsonInString = mapper.writeValueAsString(merge);
						taskProcessStatus.setStatus(Merge.Status.CONFLICTS.name());
						taskProcessStatus.setMessage(jsonInString);
						taskPromotionStatus.put(parseKey(projectKey, taskKey), taskProcessStatus);
					} catch (JsonProcessingException e) {
						e.printStackTrace();
					}
				} else {
					ApiError apiError = merge.getApiError();
					String message = apiError != null ? apiError.getMessage() : null;
					taskProcessStatus.setStatus("Promotion Error");
					taskProcessStatus.setMessage(message);
					taskPromotionStatus.put(parseKey(projectKey, taskKey), taskProcessStatus);
				}
			} catch (BusinessServiceException e) {
				e.printStackTrace();
				taskProcessStatus.setStatus("Promotion Error");
				taskProcessStatus.setMessage(e.getMessage());
				taskPromotionStatus.put(parseKey(projectKey, taskKey), taskProcessStatus);
			}
		});
	}
	
	public void doProjectPromotion(String projectKey, MergeRequest mergeRequest) {
		final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		ProcessStatus projectProcessStatus = new ProcessStatus();
		executorService.submit(() -> {
			SecurityContextHolder.getContext().setAuthentication(authentication);			
			try {
				
				projectProcessStatus.setStatus("Rebasing");
				projectProcessStatus.setMessage("Project is rebasing");
				projectPromotionStatus.put(projectKey, projectProcessStatus);
				String projectBranchPath = taskService.getProjectBranchPathUsingCache(projectKey);
				Merge merge = branchService.mergeBranchSync(projectBranchPath, PathHelper.getParentPath(projectBranchPath), mergeRequest.getSourceReviewId());
				if (merge.getStatus() == Merge.Status.COMPLETED) {
					List<Issue> promotedIssues = taskService.getTaskIssues(projectKey, TaskStatus.PROMOTED);
					taskService.stateTransition(promotedIssues, TaskStatus.COMPLETED);
					notificationService.queueNotification(ControllerHelper.getUsername(),
							new Notification(projectKey, null, EntityType.Promotion, "Project successfully promoted"));
					projectProcessStatus.setStatus("Promotion Complete");
					projectProcessStatus.setMessage("Project successfully promoted");
					projectPromotionStatus.put(projectKey, projectProcessStatus);
				} else if (merge.getStatus().equals(Merge.Status.CONFLICTS)) {
					try {
						ObjectMapper mapper = new ObjectMapper();
						String jsonInString = mapper.writeValueAsString(merge);
						projectProcessStatus.setStatus(Merge.Status.CONFLICTS.name());
						projectProcessStatus.setMessage(jsonInString);
						projectPromotionStatus.put(projectKey, projectProcessStatus);
					} catch (JsonProcessingException e) {
						e.printStackTrace();
					}
				} else {
					ApiError apiError = merge.getApiError();
					String message = apiError != null ? apiError.getMessage() : null;
					projectProcessStatus.setStatus("Promotion Error");
					projectProcessStatus.setMessage(message);
					projectPromotionStatus.put(projectKey, projectProcessStatus);
				}
			} catch (BusinessServiceException e) {
				e.printStackTrace();
				projectProcessStatus.setStatus("Promotion Error");
				projectProcessStatus.setMessage(e.getMessage());
				projectPromotionStatus.put(projectKey, projectProcessStatus);
			}
		});
		
	}
	
	private synchronized void doAutomateTaskPromotion(String projectKey, String taskKey, Authentication authentication){
		SecurityContextHolder.getContext().setAuthentication(authentication);
		try {

			// Call rebase process
			String mergeId = this.autoRebaseTask(projectKey, taskKey);
			if (null != mergeId && mergeId.equals("stopped")) {
				return;
			}

			// Call classification process
			Classification classification =  this.autoClassificationTask(projectKey, taskKey);
			if(null != classification && (classification.getResults().getRelationshipChangesCount() == 0)) {

				// Call promote process
				Merge merge = new Merge();
				merge = this.autoPromoteTask(projectKey, taskKey);
				if (merge.getStatus() == Merge.Status.COMPLETED) {
					notificationService.queueNotification(ControllerHelper.getUsername(), new Notification(projectKey, taskKey, EntityType.Promotion, "Automated promotion completed"));
					ProcessStatus status = new ProcessStatus("Completed","");
					automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
					taskService.stateTransition(projectKey, taskKey, TaskStatus.PROMOTED);
				} else {
					ProcessStatus status = new ProcessStatus("Failed",merge.getApiError().getMessage());
					automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
				}
			}
		} catch (BusinessServiceException e) {
			ProcessStatus status = new ProcessStatus("Failed",e.getMessage());
			automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
		} finally {
			SecurityContextHolder.getContext().setAuthentication(null);
		}
	}

	private Merge autoPromoteTask(String projectKey, String taskKey) throws BusinessServiceException {
		ProcessStatus status = new ProcessStatus("Promoting","");
		automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
		String taskBranchPath = taskService.getTaskBranchPathUsingCache(projectKey, taskKey);
		Merge merge = branchService.mergeBranchSync(taskBranchPath, PathHelper.getParentPath(taskBranchPath), null);
		return merge;
	}

	private Classification autoClassificationTask(String projectKey, String taskKey) throws BusinessServiceException {
		ProcessStatus status = new ProcessStatus("Classifying","");
		automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
		String branchPath = taskService.getTaskBranchPathUsingCache(projectKey, taskKey);
		try {
			Classification classification =  classificationService.startClassification(projectKey, taskKey, branchPath, ControllerHelper.getUsername());

			snowOwlRestClientFactory.getClient().waitForClassificationToComplete(classification.getResults());

			if (classification.getResults().getStatus().equals(ClassificationResults.ClassificationStatus.COMPLETED.toString())) {
				if (null != classification && null != classification.getResults() && classification.getResults().getRelationshipChangesCount() != 0) {
					status = new ProcessStatus("Classified with results",""); 
					status.setCompleteDate(new Date());
					automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
				}
			} else {
				throw new BusinessServiceException(classification.getMessage());
			}
			return classification;
		} catch (RestClientException | JSONException | InterruptedException e) {
			notificationService.queueNotification(ControllerHelper.getUsername(), new Notification(projectKey, taskKey, EntityType.Classification, "Failed to start classification."));
			throw new BusinessServiceException("Failed to classify", e);
		}
	}

	private String autoRebaseTask(String projectKey, String taskKey) throws BusinessServiceException {
		ProcessStatus status = new ProcessStatus("Rebasing","");
		automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
		String taskBranchPath = taskService.getTaskBranchPathUsingCache(projectKey, taskKey);

		// Get current task and check branch state
		AuthoringTask authoringTask = taskService.retrieveTask(projectKey, taskKey);
		String branchState = authoringTask.getBranchState();

		// Will skip rebase process if the branch state is FORWARD or UP_TO_DATE
		if ((branchState.equalsIgnoreCase(BranchState.FORWARD.toString()) || branchState.equalsIgnoreCase(BranchState.UP_TO_DATE.toString())) ) {
			return null;
		} else {
			try {
				SnowOwlRestClient client = snowOwlRestClientFactory.getClient();
				String mergeId = client.createBranchMergeReviews(PathHelper.getParentPath(taskBranchPath), taskBranchPath);
				MergeReviewsResults mergeReview;
				int sleepSeconds = 4;
				int totalWait = 0;
				int maxTotalWait = 60 * 60;
				try {
					do {
						Thread.sleep(1000 * sleepSeconds);
						totalWait += sleepSeconds;
						mergeReview = client.getMergeReviewsResult(mergeId);
						if (sleepSeconds < 10) {
							sleepSeconds+=2;
						}
					} while (totalWait < maxTotalWait && (mergeReview.getStatus() != CURRENT));

					// Check conflict of merge review
					if (client.isNoMergeConflict(mergeId)) {

						// Process rebase task
						Merge merge = branchService.mergeBranchSync(PathHelper.getParentPath(taskBranchPath), taskBranchPath, null);
						if (merge.getStatus() == Merge.Status.COMPLETED) {
							return mergeId;
						} else {
							ApiError apiError = merge.getApiError();
							String message = apiError != null ? apiError.getMessage() : null;
							notificationService.queueNotification(ControllerHelper.getUsername(), new Notification(projectKey, taskKey, EntityType.Rebase, message));
							status = new ProcessStatus("Rebased with conflicts",message);
							status.setCompleteDate(new Date());
							automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
							return "stopped";
						}
					} else {
						notificationService.queueNotification(ControllerHelper.getUsername(), new Notification(projectKey, taskKey, EntityType.Rebase, "Rebase has conflicts"));
						status = new ProcessStatus("Rebased with conflicts","");
						status.setCompleteDate(new Date());
						automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
						return "stopped";
					}
				} catch (InterruptedException | RestClientException e) {
					status = new ProcessStatus("Rebased with conflicts",e.getMessage());
					automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
					throw new BusinessServiceException("Failed to fetch merge reviews status.", e);
				}
			} catch (RestClientException e) {
				status = new ProcessStatus("Rebased with conflicts",e.getMessage());
				status.setCompleteDate(new Date());
				automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
				throw new BusinessServiceException("Failed to start merge reviews.", e);
			}

		}
	}

	private String parseKey(String projectKey, String taskKey) {
		return projectKey + "|" + taskKey;
	}
	
	public ProcessStatus getTaskPromotionStatus(String projectKey, String taskKey) {
		return taskPromotionStatus.get(parseKey(projectKey, taskKey));
	}

	public ProcessStatus getProjectPromotionStatus(String projectKey) {
		return projectPromotionStatus.get(projectKey);
	}
	
	public ProcessStatus getAutomateTaskPromotionStatus(String projectKey, String taskKey) {
		return automateTaskPromotionStatus.get(parseKey(projectKey, taskKey));
	}
	
	@PreDestroy
	public void shutdown() {
		executorService.shutdown();
	}

	
}
