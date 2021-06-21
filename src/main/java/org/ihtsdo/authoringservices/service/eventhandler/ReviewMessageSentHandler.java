package org.ihtsdo.authoringservices.service.eventhandler;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.authoringservices.domain.AuthoringTask;
import org.ihtsdo.authoringservices.domain.EntityType;
import org.ihtsdo.authoringservices.domain.Notification;
import org.ihtsdo.authoringservices.domain.User;
import org.ihtsdo.authoringservices.entity.Branch;
import org.ihtsdo.authoringservices.entity.ReviewMessage;
import org.ihtsdo.authoringservices.service.ReviewMessageSentListener;
import org.ihtsdo.authoringservices.service.NotificationService;
import org.ihtsdo.authoringservices.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ReviewMessageSentHandler implements ReviewMessageSentListener {

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private TaskService taskService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	public void messageSent(ReviewMessage message, String event) {
		try {
			final List<String> usersToNotify = new ArrayList<>();
			final Branch branch = message.getBranch();
			final String project = branch.getProject();
			final String task = branch.getTask();
			final AuthoringTask authoringTask = taskService.retrieveTask(project, task, true);
			addIfNotNull(usersToNotify, authoringTask.getAssignee());
			if (authoringTask.getReviewers() != null) {
				authoringTask.getReviewers().forEach(reviewer -> addIfNotNull(usersToNotify, reviewer));
			}
			usersToNotify.remove(message.getFromUsername());
			logger.info("Feedback message for task {} notification to {}", task, usersToNotify);
			for (String username : usersToNotify) {
				notificationService.queueNotification(username, new Notification(project, task, EntityType.Feedback, event));
			}
		} catch (BusinessServiceException e) {
			logger.error("Failed to notify user of review feedback.", e);
		}
	}

	private void addIfNotNull(List<String> usersToNotify, User user) {
		if (user != null) {
			usersToNotify.add(user.getUsername());
		}
	}
}
