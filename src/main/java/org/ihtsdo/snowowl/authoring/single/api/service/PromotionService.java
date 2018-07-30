package org.ihtsdo.snowowl.authoring.single.api.service;

import static org.ihtsdo.otf.rest.client.snowowl.pojo.MergeReviewsResults.MergeReviewStatus.CURRENT;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
import org.ihtsdo.snowowl.authoring.single.api.pojo.Classification;
import org.ihtsdo.snowowl.authoring.single.api.pojo.EntityType;
import org.ihtsdo.snowowl.authoring.single.api.pojo.MergeRequest;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Notification;
import org.ihtsdo.snowowl.authoring.single.api.pojo.ProcessStatus;
import org.ihtsdo.snowowl.authoring.single.api.rest.ControllerHelper;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.BranchState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
	
	private ProcessStatus processStatus;

	private final ExecutorService executorService;

	public PromotionService() {
		automateTaskPromotionStatus = new HashMap<>();
		taskPromotionStatus = new HashMap<>();
		processStatus = new ProcessStatus();
		executorService = Executors.newCachedThreadPool();
	}

	public void doAutomateTaskPromotion(String projectKey, String taskKey) throws BusinessServiceException {
		final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		executorService.submit(() -> {
			processStatus.setStatus("Queued");
			processStatus.setMessage("");
			SecurityContextHolder.getContext().setAuthentication(authentication);
			automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), processStatus);
			doAutomateTaskPromotion(projectKey, taskKey, authentication);
		});
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
	
	private synchronized void doAutomateTaskPromotion(String projectKey, String taskKey, Authentication authentication){
		try {

			// Call rebase process
			Merge merge = new Merge();
			String mergeId = this.autoRebaseTask(projectKey, taskKey);

			if (null != mergeId && mergeId.equals("stopped")) {
				return;
			}

			// Call classification process
			Classification classification =  this.autoClassificationTask(projectKey, taskKey);
			if(null != classification && (classification.getResults().getRelationshipChangesCount() == 0)) {

				// Call promote process
				merge = this.autoPromoteTask(projectKey, taskKey, mergeId);
				if (merge.getStatus() == Merge.Status.COMPLETED) {
					notificationService.queueNotification(ControllerHelper.getUsername(), new Notification(projectKey, taskKey, EntityType.Promotion, "Automated promotion completed"));
					processStatus.setStatus("Completed");
					automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), processStatus);
					taskService.stateTransition(projectKey, taskKey, TaskStatus.PROMOTED);
				} else {
					processStatus.setStatus("Failed");
					automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), processStatus);
					processStatus.setMessage(merge.getApiError().getMessage());
				}
			}
		} catch (BusinessServiceException e) {
			processStatus.setStatus("Failed");
			processStatus.setMessage(e.getMessage());
			automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), processStatus);
		}
	}

	private Merge autoPromoteTask(String projectKey, String taskKey, String mergeId) throws BusinessServiceException {
		processStatus.setStatus("Promoting");
		processStatus.setMessage("");
		automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), processStatus);
		String taskBranchPath = taskService.getTaskBranchPathUsingCache(projectKey, taskKey);
		Merge merge = branchService.mergeBranchSync(taskBranchPath, PathHelper.getParentPath(taskBranchPath), mergeId);
		return merge;
	}

	private Classification autoClassificationTask(String projectKey, String taskKey) throws BusinessServiceException {
		processStatus.setStatus("Classifying");
		processStatus.setMessage("");
		automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), processStatus);
		String branchPath = taskService.getTaskBranchPathUsingCache(projectKey, taskKey);
		try {
			Classification classification =  classificationService.startClassification(projectKey, taskKey, branchPath, ControllerHelper.getUsername());

			snowOwlRestClientFactory.getClient().waitForClassificationToComplete(classification.getResults());

			if (classification.getResults().getStatus().equals(ClassificationResults.ClassificationStatus.COMPLETED.toString())) {
				if (null != classification && null != classification.getResults() && classification.getResults().getRelationshipChangesCount() != 0) {
					processStatus.setStatus("Classified with results");
					processStatus.setMessage("");
					automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), processStatus);
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
		processStatus.setStatus("Rebasing");
		processStatus.setMessage("");
		automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), processStatus);
		String taskBranchPath = taskService.getTaskBranchPathUsingCache(projectKey, taskKey);

		// Get current task and check branch state
		AuthoringTask authoringTask = taskService.retrieveTask(projectKey, taskKey);
		String branchState = authoringTask.getBranchState();
		if (null != authoringTask) {

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
								processStatus.setStatus("Rebased with conflicts");
								processStatus.setMessage(message);
								automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), processStatus);
								return "stopped";
							}
						} else {
							notificationService.queueNotification(ControllerHelper.getUsername(), new Notification(projectKey, taskKey, EntityType.Rebase, "Rebase has conflicts"));
							processStatus.setStatus("Rebased with conflicts");
							automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), processStatus);
							return "stopped";
						}
					} catch (InterruptedException | RestClientException e) {
						processStatus.setStatus("Rebased with conflicts");
						processStatus.setMessage(e.getMessage());
						automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), processStatus);
						throw new BusinessServiceException("Failed to fetch merge reviews status.", e);
					}
				} catch (RestClientException e) {
					processStatus.setStatus("Rebased with conflicts");
					processStatus.setMessage(e.getMessage());
					automateTaskPromotionStatus.put(parseKey(projectKey, taskKey), processStatus);
					throw new BusinessServiceException("Failed to start merge reviews.", e);
				}

			}
		}
		return null;
	}

	public ProcessStatus getAutomateTaskPromotionStatus(String projectKey, String taskKey) {
		return automateTaskPromotionStatus.get(parseKey(projectKey, taskKey));
	}

	private String parseKey(String projectKey, String taskKey) {
		return projectKey + "|" + taskKey;
	}

	@PreDestroy
	public void shutdown() {
		executorService.shutdown();
	}

	public ProcessStatus getTaskPromotionStatus(String projectKey, String taskKey) {
		return taskPromotionStatus.get(parseKey(projectKey, taskKey));
	}

}
