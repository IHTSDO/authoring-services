package org.ihtsdo.authoringservices.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.rcarz.jiraclient.Issue;
import org.ihtsdo.authoringservices.domain.*;
import org.ihtsdo.authoringservices.review.pojo.BranchState;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.PathHelper;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ApiError;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ClassificationStatus;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Merge;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import us.monoid.json.JSONException;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class PromotionService {

	@Autowired
	private TaskService taskService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private SnowstormRestClientFactory snowstormRestClientFactory;

	@Autowired
	private BranchService branchService;

	@Autowired
	private SnowstormClassificationClient classificationService;

	private final Map<String, ProcessStatus> automateTaskPromotionStatus;

	private final Map<String, ProcessStatus> taskPromotionStatus;

	private final Map<String, ProcessStatus> projectPromotionStatus;

	private final ExecutorService executorService;

	private final LinkedBlockingQueue<AutomatePromoteProcess> autoPromoteBlockingQueue = new LinkedBlockingQueue<>();

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

	public void queueAutomateTaskPromotion(String projectKey, String taskKey) {
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
					notificationService.queueNotification(SecurityUtil.getUsername(),
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
					taskService.stateTransition(promotedIssues, TaskStatus.COMPLETED, projectKey);
					notificationService.queueNotification(SecurityUtil.getUsername(),
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
			String rebaseStatus = this.autoRebaseTask(projectKey, taskKey);
			if (null != rebaseStatus && rebaseStatus.equals("stopped")) {
				return;
			}

			// Call classification process
			Classification classification = this.autoClassificationTask(projectKey, taskKey);
			if (classification != null && !classification.getResults().isInferredRelationshipChangesFound()) {

				// Call promote process
				Merge merge = this.autoPromoteTask(projectKey, taskKey);
				if (merge.getStatus() == Merge.Status.COMPLETED) {
					notificationService.queueNotification(SecurityUtil.getUsername(), new Notification(projectKey, taskKey, EntityType.Promotion, "Automated promotion completed"));
					ProcessStatus status = new ProcessStatus("Completed","");
					automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
					taskService.stateTransition(projectKey, taskKey, TaskStatus.PROMOTED);
				} else {
					ProcessStatus status = new ProcessStatus("Failed",merge.getApiError() == null ? "" : merge.getApiError().getMessage());
					automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
				}
			}
		} catch (BusinessServiceException e) {
			ProcessStatus status = new ProcessStatus("Failed", e.getMessage());
			automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
			logger.error("Failed to auto promote task " + taskKey , e);
		} finally {
			SecurityContextHolder.getContext().setAuthentication(null);
		}
	}

	private Merge autoPromoteTask(String projectKey, String taskKey) throws BusinessServiceException {
		ProcessStatus status = new ProcessStatus("Promoting","");
		automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
		String taskBranchPath = taskService.getTaskBranchPathUsingCache(projectKey, taskKey);
        return branchService.mergeBranchSync(taskBranchPath, PathHelper.getParentPath(taskBranchPath), null);
	}

	private Classification autoClassificationTask(String projectKey, String taskKey) throws BusinessServiceException {
		ProcessStatus status = new ProcessStatus("Classifying","");
		automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
		String branchPath = taskService.getTaskBranchPathUsingCache(projectKey, taskKey);
		try {
			Classification classification =  classificationService.startClassification(projectKey, taskKey, branchPath, SecurityUtil.getUsername());
			classification.setResults(snowstormRestClientFactory.getClient().waitForClassificationToComplete(classification.getResults()));
			if (ClassificationStatus.COMPLETED == classification.getResults().getStatus()) {
				if (classification.getResults().isInferredRelationshipChangesFound()) {
					status = new ProcessStatus("Classified with results","");
					status.setCompleteDate(new Date());
					automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
				}
			} else {
				throw new BusinessServiceException(classification.getMessage());
			}
			taskService.clearClassificationCache(taskService.getBranchPathUsingCache(projectKey, taskKey));
			return classification;
		} catch (RestClientException | JSONException | InterruptedException e) {
			notificationService.queueNotification(SecurityUtil.getUsername(), new Notification(projectKey, taskKey, EntityType.Classification, "Failed to start classification"));
			taskService.clearClassificationCache(taskService.getBranchPathUsingCache(projectKey, taskKey));
			throw new BusinessServiceException("Failed to classify", e);
		} catch (IllegalStateException e) {
			notificationService.queueNotification(SecurityUtil.getUsername(), new Notification(projectKey, taskKey, EntityType.Classification, "Failed to start classification due to classification already in progress"));
			status = new ProcessStatus("Classification in progress",e.getMessage());
			automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
			taskService.clearClassificationCache(taskService.getBranchPathUsingCache(projectKey, taskKey));
			return null;
		}
	}

	@SuppressWarnings("rawtypes")
	private String autoRebaseTask(String projectKey, String taskKey) throws BusinessServiceException {
		ProcessStatus status = new ProcessStatus("Rebasing","");
		automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
		String taskBranchPath = taskService.getTaskBranchPathUsingCache(projectKey, taskKey);

		// Get current task and check branch state
		AuthoringTask authoringTask = taskService.retrieveTask(projectKey, taskKey, true);
		String branchState = authoringTask.getBranchState();

		// Will skip rebase process if the branch state is FORWARD or UP_TO_DATE
		if ((branchState.equalsIgnoreCase(BranchState.FORWARD.toString()) || branchState.equalsIgnoreCase(BranchState.UP_TO_DATE.toString())) ) {
			return null;
		} else {
			try {
				String mergeId = branchService.generateBranchMergeReviews(PathHelper.getParentPath(taskBranchPath), taskBranchPath);
				SnowstormRestClient client = snowstormRestClientFactory.getClient();
				Set mergeReviewResult =  client.getMergeReviewsDetails(mergeId);

				// Check conflict of merge review
				if (mergeReviewResult.isEmpty()) {

					// Process rebase task
					Merge merge = branchService.mergeBranchSync(PathHelper.getParentPath(taskBranchPath), taskBranchPath, mergeId);
					if (merge.getStatus() == Merge.Status.COMPLETED) {
						return merge.getStatus().name();
					} else {
						ApiError apiError = merge.getApiError();
						String message = apiError != null ? apiError.getMessage() : null;
						notificationService.queueNotification(SecurityUtil.getUsername(), new Notification(projectKey, taskKey, EntityType.Rebase, message));
						status = new ProcessStatus("Rebased with conflicts",message);
						status.setCompleteDate(new Date());
						automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
						return "stopped";
					}
				} else {
					notificationService.queueNotification(SecurityUtil.getUsername(), new Notification(projectKey, taskKey, EntityType.Rebase, "Rebase has conflicts"));
					status = new ProcessStatus("Rebased with conflicts","");
					status.setCompleteDate(new Date());
					automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
					return "stopped";
				}
			} catch (InterruptedException e) {
				status = new ProcessStatus("Rebased with conflicts",e.getMessage());
				automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), status);
				throw new BusinessServiceException("Failed to fetch merge reviews status.", e);
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

	public void clearAutomateTaskPromotionStatus(String projectKey, String taskKey) {
		if (!automateTaskPromotionStatus.containsKey(parseKey(projectKey, taskKey))) {
			throw new ResourceNotFoundException(String.format("Automated promotion status not found for task %s", taskKey));
		}
		automateTaskPromotionStatus.remove(parseKey(projectKey, taskKey));
	}

	@PreDestroy
	public void shutdown() {
		executorService.shutdown();
	}
}
