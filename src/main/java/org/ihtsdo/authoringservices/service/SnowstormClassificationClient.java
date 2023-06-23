package org.ihtsdo.authoringservices.service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ihtsdo.authoringservices.domain.*;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ClassificationResults;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ClassificationStatus;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import us.monoid.json.JSONException;

import javax.jms.JMSException;
import javax.jms.TextMessage;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class SnowstormClassificationClient {

	@Autowired
	private TaskService taskService;

	@Autowired
	private SnowstormRestClientFactory snowstormRestClientFactory;

	@Autowired
	private NotificationService notificationService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final Map <String, ClassificationRequest> classificationRequests = Collections.synchronizedMap(new HashMap <>());

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

	@JmsListener(destination = "${classification.status.queue}")
	void messageConsumer(TextMessage statusResponseMessage) throws JMSException, JsonProcessingException {
		try {
			ClassificationStatusResponse response = objectMapper.readValue(statusResponseMessage.getText(), ClassificationStatusResponse.class);
			if (classificationRequests.containsKey(response.getId())) {
				if (!ClassificationStatus.RUNNING.equals(response.getStatus())
					&& !ClassificationStatus.SCHEDULED.equals(response.getStatus())
					&& !ClassificationStatus.SAVING_IN_PROGRESS.equals(response.getStatus())) {
					ClassificationRequest request = classificationRequests.remove(response.getId());
					new Thread(new ClassificationRunner(request, response.getStatus())).start();
				}
			}
		} catch (JsonParseException | JsonMappingException e) {
			logger.error("Failed to parse message. Message: {}.", statusResponseMessage.getText());
		}
	}


	private Classification callClassification(String projectKey, String taskKey, String branchPath, String callerUsername) throws RestClientException {
		logger.info("Requesting classification of path {} for user {}", branchPath, callerUsername);

		ClassificationResults results = snowstormRestClientFactory.getClient().startClassification(branchPath);
		//If we started the classification without an exception then it's state will be RUNNING (or queued)
		results.setStatus(ClassificationStatus.RUNNING);

		ClassificationRequest request = new ClassificationRequest();
		request.setClassificationId(results.getClassificationId());
		request.setTaskKey(taskKey);
		request.setProjectKey(projectKey);
		request.setBranchPath(branchPath);
		request.setAuthentication(SecurityContextHolder.getContext().getAuthentication());
		classificationRequests.put(results.getClassificationId(), request);

		return new Classification(results);
	}

	private class ClassificationRunner implements Runnable {

		private static final int PAUSE_SECONDS = 10;

		private ClassificationRequest request;
		private ClassificationStatus status;

		ClassificationRunner(ClassificationRequest request, ClassificationStatus status) {
			this.request = request;
			this.status = status;
		}

		@Override
		public void run() {
			SecurityContextHolder.getContext().setAuthentication(request.getAuthentication());
			String resultMessage;
			if (ClassificationStatus.COMPLETED.equals(status)) {
				resultMessage = "Classification completed successfully";
			} else {
				resultMessage = "Classification failed to complete due to an internal error. Please try again.";
			}

			if (request.getTaskKey() != null) {
				//In every case we'll report what we know to the jira ticket
				taskService.addCommentLogErrors(request.getProjectKey(), request.getTaskKey(), resultMessage);
			} else if (request.getProjectKey() != null) {
				// Comment on project magic ticket
				taskService.addCommentLogErrors(request.getProjectKey(), resultMessage);
			}

			// Pause few seconds to make sure that the Snowstorm updates the classification results before clearing the cache
			try {
				Thread.sleep(PAUSE_SECONDS * 1000);
				taskService.clearClassificationCache(request.getBranchPath());
			}
			catch (InterruptedException e) {
				// This will probably happen when we restart the application.
				logger.info("Classification interrupted.", e);
			}

			Notification notification = new Notification(request.getProjectKey(), request.getTaskKey(), EntityType.Classification, resultMessage);
			notification.setBranchPath(request.getBranchPath());
			notificationService.queueNotification(SecurityUtil.getUsername(), notification);
		}
	}
}
