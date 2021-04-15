package org.ihtsdo.authoringservices.service;

import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ClassificationResults;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ClassificationStatus;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.authoringservices.domain.Classification;
import org.ihtsdo.authoringservices.domain.EntityType;
import org.ihtsdo.authoringservices.domain.Notification;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import us.monoid.json.JSONException;

@Service
public class SnowstormClassificationClient {

	@Autowired
	private TaskService taskService;

	@Autowired
	private SnowstormRestClientFactory snowstormRestClientFactory;

	@Autowired
	private NotificationService notificationService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public synchronized Classification startClassification(String projectKey, String taskKey, String branchPath, String username) throws RestClientException, JSONException, BusinessServiceException {
		if (!snowstormRestClientFactory.getClient().isClassificationInProgressOnBranch(branchPath)) {
			return callClassification(projectKey, taskKey, branchPath, username);
		} else {
			throw new IllegalStateException("Classification already in progress on this branch.");
		}
	}

	@Cacheable(value = "classification-status", key = "#branchPath")
	public String getLatestClassification(String branchPath) throws RestClientException {
		return snowstormRestClientFactory.getClient().getLatestClassificationOnBranch(branchPath);
	}

	@CacheEvict(value = "classification-status", key = "#branchPath")
	public void evictClassificationCache(String branchPath) {
		logger.info("Cleared Classification cache for branch {}.", branchPath);
	}

	private Classification callClassification(String projectKey, String taskKey, String branchPath, String callerUsername) throws RestClientException {
		logger.info("Requesting classification of path {} for user {}", branchPath, callerUsername);
		ClassificationResults results = snowstormRestClientFactory.getClient().startClassification(branchPath);
		//If we started the classification without an exception then it's state will be RUNNING (or queued)
		results.setStatus(ClassificationStatus.RUNNING);


		//Now start an asynchronous thread to wait for the results
		final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		new Thread(new ClassificationPoller(projectKey, taskKey, results, authentication), "ClassificationPoller-" + callerUsername).start();

		return new Classification(results);
	}

	private class ClassificationPoller implements Runnable {

		private ClassificationResults results;
		private String projectKey;
		private String taskKey;
		private final Authentication authentication;

		ClassificationPoller(String projectKey, String taskKey, ClassificationResults results, Authentication authentication) {
			this.results = results;
			this.projectKey = projectKey;
			this.taskKey = taskKey;
			this.authentication = authentication;
		}

		@Override
		public void run() {
			SecurityContextHolder.getContext().setAuthentication(authentication);
			String resultMessage = null;
			String branchPath = null;
			SnowstormRestClient terminologyServerClient = snowstormRestClientFactory.getClient();
			try {
				// Function sleeps here
				// - Throws RestClientException if classification failed
				terminologyServerClient.waitForClassificationToComplete(results);
				resultMessage = "Classification completed successfully";
			} catch (RestClientException | InterruptedException e) {
				resultMessage = "Classification failed to complete due to an internal error. Please try again.";
				logger.error(resultMessage, e);
			}

			try {
			if (taskKey != null) {
				//In every case we'll report what we know to the jira ticket
				taskService.addCommentLogErrors(projectKey, taskKey, resultMessage);
				branchPath = taskService.getBranchPathUsingCache(projectKey, taskKey);

			} else {
				// Comment on project magic ticket
				taskService.addCommentLogErrors(projectKey, resultMessage);
				branchPath = taskService.getProjectBranchPathUsingCache(projectKey);
			}
			} catch (BusinessServiceException e) {
				logger.error(e.getMessage(), e);
			}
			taskService.clearClassificationCache(branchPath);
			notificationService.queueNotification(SecurityUtil.getUsername(), new Notification(projectKey, taskKey, EntityType.Classification, resultMessage));
		}

	}

}
