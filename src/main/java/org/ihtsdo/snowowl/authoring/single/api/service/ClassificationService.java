package org.ihtsdo.snowowl.authoring.single.api.service;

import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.snowowl.SnowOwlRestClient;
import org.ihtsdo.otf.rest.client.snowowl.pojo.ClassificationResults;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Classification;
import org.ihtsdo.snowowl.authoring.single.api.pojo.EntityType;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Notification;
import org.ihtsdo.snowowl.authoring.single.api.rest.ControllerHelper;
import org.ihtsdo.otf.rest.client.snowowl.SnowOwlRestClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import us.monoid.json.JSONException;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class ClassificationService {
	
	@Autowired
	private TaskService taskService;

	@Autowired
	private SnowOwlRestClientFactory snowOwlRestClientFactory;

	@Autowired
	private NotificationService notificationService;

	private Logger logger = LoggerFactory.getLogger(getClass());

	public synchronized Classification startClassification(String projectKey, String taskKey, String branchPath, String username) throws RestClientException, JSONException, BusinessServiceException {
		if (!snowOwlRestClientFactory.getClient().isClassificationInProgressOnBranch(branchPath)) {
			return callClassification(projectKey, taskKey, branchPath, username);
		} else {
			throw new IllegalStateException("Classification already in progress on this branch.");
		}
	}

	public String getLatestClassification(String branchPath) throws RestClientException {
		return snowOwlRestClientFactory.getClient().getLatestClassificationOnBranch(branchPath);
	}

	private Classification callClassification(String projectKey, String taskKey, String branchPath, String callerUsername) throws RestClientException {
		logger.info("Requesting classification of path {} for user {}", branchPath, callerUsername);
		ClassificationResults results = snowOwlRestClientFactory.getClient().startClassification(branchPath);
		//If we started the classification without an exception then it's state will be RUNNING (or queued)
		results.setStatus(ClassificationResults.ClassificationStatus.RUNNING.toString());

		//Now start an asynchronous thread to wait for the results
		final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		(new Thread(new ClassificationPoller(branchPath, projectKey, taskKey, results, authentication))).start();

		return new Classification(results);
	}

	private class ClassificationPoller implements Runnable {

		private Date startDate;
		private final String branchPath;
		private ClassificationResults results;
		private String projectKey;
		private String taskKey;
		private final Authentication authentication;

		ClassificationPoller(String branchPath, String projectKey, String taskKey, ClassificationResults results, Authentication authentication) {
			this.startDate = new Date();
			this.branchPath = branchPath;
			this.results = results;
			this.projectKey = projectKey;
			this.taskKey = taskKey;
			this.authentication = authentication;
		}

		@Override
		public void run() {
			SecurityContextHolder.getContext().setAuthentication(authentication);
			String resultMessage = null;
			SnowOwlRestClient terminologyServerClient = snowOwlRestClientFactory.getClient();
			try {

				try {
					// Function sleeps here
					// - Throws RestClientException if classification failed
					terminologyServerClient.waitForClassificationToComplete(results);
				} catch (RestClientException e) {
					if (terminologyServerClient.isUseExternalClassificationService() && startDate.after(getTimeSecondsInPast(21))) {
						// Retry classification just once
						logger.info("Classification failed on {} in the first {} seconds. Probably due to lack of concurrent RF2 export support in Snow Owl. " +
								"Waiting 30 seconds before retrying once.", branchPath, 20);
						Thread.sleep(30_000);
						results = terminologyServerClient.startClassification(branchPath);
						terminologyServerClient.waitForClassificationToComplete(results);
					} else {
						// Let exception bubble up
						throw e;
					}
				}
				resultMessage = "Classification completed successfully";

			} catch (RestClientException | InterruptedException e) {
				resultMessage = "Classification failed to complete due to an internal error. Please try again.";
				logger.error(resultMessage, e);
			}

			if (taskKey != null) {
				//In every case we'll report what we know to the jira ticket
				taskService.addCommentLogErrors(projectKey, taskKey, resultMessage);
			} else {
				// Comment on project magic ticket
				taskService.addCommentLogErrors(projectKey, resultMessage);
			}
			notificationService.queueNotification(ControllerHelper.getUsername(), new Notification(projectKey, taskKey, EntityType.Classification, resultMessage));
		}

		private Date getTimeSecondsInPast(int seconds) {
			GregorianCalendar calendar = new GregorianCalendar();
			calendar.add(Calendar.SECOND, -seconds);
			return calendar.getTime();
		}

	}

}
