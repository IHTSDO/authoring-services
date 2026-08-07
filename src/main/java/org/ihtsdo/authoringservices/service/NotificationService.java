package org.ihtsdo.authoringservices.service;

import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import org.apache.commons.collections.CollectionUtils;
import org.ihtsdo.authoringservices.domain.AuthoringInfoWrapper;
import org.ihtsdo.authoringservices.domain.Notification;
import org.ihtsdo.authoringservices.domain.NotificationSeverity;
import org.ihtsdo.authoringservices.service.monitor.MonitorService;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Classification;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ClassificationStatus;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpSubscriptionMatcher;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.ihtsdo.authoringservices.service.SnowstormClassificationClient.CLASSIFICATION_RUNNING;

@Service
public class NotificationService {

	public static final String CLASSIFICATION = "classification";
	public static final String CODE_SYSTEM = "codeSystem";
	public static final String PROJECT = "project";
	public static final String TASK = "task";
	public static final String FOR = " for ";

	private final BranchService branchService;

	private final SnowstormClassificationClient classificationClient;

	private final SimpMessagingTemplate simpMessagingTemplate;

	private final SimpUserRegistry simpUserRegistry;

	private final MonitorService monitorService;

	private final Map<String, List<Notification>> pendingNotifications = new HashMap<>();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	public NotificationService(@Lazy BranchService branchService, @Lazy SnowstormClassificationClient classificationClient, SimpMessagingTemplate simpMessagingTemplate, SimpUserRegistry simpUserRegistry, @Lazy MonitorService monitorService) {
		this.branchService = branchService;
		this.classificationClient = classificationClient;
		this.simpMessagingTemplate = simpMessagingTemplate;
		this.simpUserRegistry = simpUserRegistry;
		this.monitorService = monitorService;
	}

	public void queueNotification(String username, Notification notification) {
		final String projectKey = notification.getProject();
		if (!Strings.isNullOrEmpty(projectKey)) {
			try {
				notification.setBranchPath(branchService.getProjectOrTaskBranchPathUsingCache(projectKey, notification.getTask()));
			} catch (BusinessServiceException _) {
				logger.error("Failed to retrieve project base for {}", projectKey);
			}
		}
		enrichNotification(notification);
		logger.info("Notification for user {} - '{}'", username, notification);
		synchronized (pendingNotifications) {
			pendingNotifications.computeIfAbsent(username, ExpiringNotificationList::new).add(notification);
			sendNotification();
		}
	}

	public void sendNotification() {
		if (logger.isDebugEnabled()) {
			logger.debug("Current users: {}", simpUserRegistry.getUsers());
		}

		Set<SimpUser> currentUsers = simpUserRegistry.getUsers();
		for (SimpUser simpUser : currentUsers) {
			String username =  simpUser.getName();
			monitorService.keepMonitorsAlive(username);

			SimpSubscriptionMatcher simpSubscriptionMatcher = subscription -> subscription.getDestination().equals("/topic/user/" + username + "/notifications");
			Set<SimpSubscription> simpSubscriptions = simpUserRegistry.findSubscriptions(simpSubscriptionMatcher);
			if (!simpSubscriptions.isEmpty()) {
				sendNotification(username);
			}
		}
	}
	
	public void sendNotification(String username) {
		if (pendingNotifications.containsKey(username)) {
			synchronized (pendingNotifications) {
				List<Notification> notifications = pendingNotifications.remove(username);
				if (!CollectionUtils.isEmpty(notifications)) {
					for (Notification notification : notifications) {
						simpMessagingTemplate.convertAndSend("/topic/user/" + username + "/notifications", notification);
					}
				}
			}
		}
	}

	private void enrichNotification(Notification notification) {
		if (notification == null || notification.getEntityType() == null) {
			return;
		}

		switch (notification.getEntityType()) {
			case Promotion -> enrichPromotion(notification);
			case Feedback -> enrichFeedback(notification);
			case Classification -> enrichClassification(notification);
			case BranchState -> enrichBranchState(notification);
			case Rebase -> enrichRebase(notification);
			case Validation -> enrichValidation(notification);
			case BranchHead -> enrichBranchHead(notification);
			case AuthorChange -> enrichAuthorChange(notification);
			case Inactivation -> enrichInactivation(notification);
		}
	}

	private void enrichPromotion(Notification notification) {
		String message = notification.getEvent();
		if (!Strings.isNullOrEmpty(notification.getTask())) {
			message += FOR + notification.getTask();
			notification.setDeepLinkPath(taskPath(notification.getProject(), notification.getTask(), "edit"));
			notification.setRequiresRefetch(List.of("promotion", TASK));
		} else {
			message += FOR + notification.getProject();
			notification.setDeepLinkPath("/" + PROJECT + "/" + notification.getProject());
			notification.setRequiresRefetch(List.of("promotion", PROJECT));
		}
		notification.setNotificationMessage(message);
		notification.setSeverity(NotificationSeverity.INFO);
	}

	private void enrichFeedback(Notification notification) {
		String event = capitalizeWords(notification.getEvent());
		notification.setNotificationMessage(event + " feedback for task " + notification.getTask());
		notification.setDeepLinkPath(taskPath(notification.getProject(), notification.getTask(), "feedback"));
		notification.setSeverity(NotificationSeverity.INFO);
	}

	private void enrichClassification(Notification notification) {
		List<String> refetch = new ArrayList<>();
		refetch.add(CLASSIFICATION);

		if (notification.getTask() != null) {
			refetch.add(TASK);
		} else if (notification.getProject() != null) {
			refetch.add(PROJECT);
		} else {
			refetch.add(CODE_SYSTEM);
		}
		notification.setRequiresRefetch(refetch);
		notification.setDeepLinkPath(resolveClassificationDeepLink(notification));
		if (CLASSIFICATION_RUNNING.equals(notification.getEvent())) {
			notification.setNotificationMessage(CLASSIFICATION_RUNNING);
			notification.setSeverity(NotificationSeverity.INFO);
			return;
		}

		String targetLabel = resolveTargetLabel(notification);
		String message = notification.getEvent() + FOR + targetLabel;

		if (isClassificationFailureEvent(notification.getEvent())) {
			notification.setNotificationMessage(message);
			notification.setSeverity(NotificationSeverity.ERROR);
			return;
		}

		appendClassificationOutcome(notification, message);
	}

	private void enrichBranchState(Notification notification) {
		if ("DIVERGED".equals(notification.getEvent())) {
			notification.setRequiresRefetch(List.of("branchState"));
		}
	}

	private void enrichRebase(Notification notification) {
		notification.setNotificationMessage(notification.getEvent());
		notification.setSeverity(NotificationSeverity.INFO);

		if (!Strings.isNullOrEmpty(notification.getTask())) {
			notification.setDeepLinkPath(taskPath(notification.getProject(), notification.getTask(), "edit"));
			notification.setRequiresRefetch(List.of(TASK));
		} else {
			notification.setDeepLinkPath("/" + PROJECT + "/" + notification.getProject());
			notification.setRequiresRefetch(List.of(PROJECT));
		}
	}

	private void enrichValidation(Notification notification) {
		List<String> refetch = new ArrayList<>();
		refetch.add("validation");

		if (notification.getTask() != null) {
			refetch.add(TASK);
		} else if (notification.getProject() != null) {
			refetch.add(PROJECT);
		} else {
			refetch.add(CODE_SYSTEM);
		}
		notification.setRequiresRefetch(refetch);
		notification.setDeepLinkPath(resolveValidationDeepLink(notification));
		String event = notification.getEvent();
		if (event == null) {
			return;
		}
		if (!"FAILED".equalsIgnoreCase(event) && !"COMPLETED".equalsIgnoreCase(event)) {
			notification.setSeverity(NotificationSeverity.INFO);
			notification.setNotificationMessage("Validation is running");
			return;
		}

		String displayEvent = event.toLowerCase();
		String targetLabel = resolveTargetLabel(notification);
		notification.setNotificationMessage("Validation " + displayEvent + FOR + targetLabel);

		if ("FAILED".equalsIgnoreCase(event)) {
			notification.setSeverity(NotificationSeverity.ERROR);
			return;
		}
		notification.setSeverity(NotificationSeverity.INFO);
	}

	private void enrichBranchHead(Notification notification) {
		notification.setRequiresRefetch(List.of("acceptance-criteria"));
	}

	private void enrichAuthorChange(Notification notification) {
		notification.setNotificationMessage(notification.getEvent());
		notification.setSeverity(NotificationSeverity.INFO);
		notification.setRequiresRefetch(List.of(TASK));
		if (!Strings.isNullOrEmpty(notification.getTask())) {
			notification.setDeepLinkPath(taskPath(notification.getProject(), notification.getTask(), "edit"));
		} else {
			notification.setDeepLinkPath("/" + PROJECT + "/" + notification.getProject());
		}
	}

	private void enrichInactivation(Notification notification) {
		notification.setNotificationMessage(notification.getEvent());
		notification.setSeverity(NotificationSeverity.INFO);
		notification.setRequiresRefetch(List.of(TASK));
		if (!Strings.isNullOrEmpty(notification.getTask())) {
			notification.setDeepLinkPath(taskPath(notification.getProject(), notification.getTask(), "edit"));
		} else {
			notification.setDeepLinkPath("/" + PROJECT + "/" + notification.getProject());
		}
	}

	private void appendClassificationOutcome(Notification notification, String baseMessage) {
		String branchPath = notification.getBranchPath();
		if (Strings.isNullOrEmpty(branchPath)) {
			notification.setNotificationMessage(baseMessage);
			notification.setSeverity(NotificationSeverity.INFO);
			notification.setDeepLinkPath(resolveClassificationDeepLink(notification));
			return;
		}

		try {
			Classification classification = classificationClient.getLatestClassification(branchPath);
			if (classification == null) {
				notification.setNotificationMessage(baseMessage + " but no classifications could be retrieved");
				notification.setSeverity(NotificationSeverity.ERROR);
				return;
			}

			String message = baseMessage;
			if (ClassificationStatus.COMPLETED.equals(classification.getStatus())
					&& (classification.hasEquivalentConceptsFound()
					|| classification.hasInferredRelationshipChangesFound())) {
				message += " - Changes found";
			} else {
				message += " - No changes found";
			}

			notification.setNotificationMessage(message);
			notification.setSeverity(NotificationSeverity.INFO);
			notification.setDeepLinkPath(resolveClassificationDeepLink(notification));

			if (notification.getTask() != null) {
				notification.setRequiresRefetch(List.of(CLASSIFICATION, TASK));
			} else if (notification.getProject() != null) {
				notification.setRequiresRefetch(List.of(CLASSIFICATION, PROJECT));
			} else {
				notification.setRequiresRefetch(List.of(CLASSIFICATION, CODE_SYSTEM));
			}
		} catch (RestClientException e) {
			logger.error("Failed to retrieve classification for branch {}", branchPath, e);
			notification.setNotificationMessage(baseMessage + " but no classifications could be retrieved");
			notification.setSeverity(NotificationSeverity.ERROR);
		}
	}

	private String resolveTargetLabel(Notification notification) {
		if (!Strings.isNullOrEmpty(notification.getTask())) {
			return "task " + notification.getTask();
		}
		if (!Strings.isNullOrEmpty(notification.getProject())) {
			return "project " + notification.getProject();
		}
		String codeSystemShortName = resolveCodeSystemShortName(notification.getBranchPath());
		return "code system " + (codeSystemShortName != null ? codeSystemShortName : "unknown");
	}

	private String resolveCodeSystemShortName(String branchPath) {
		if (Strings.isNullOrEmpty(branchPath)) {
			return null;
		}
		try {
			AuthoringInfoWrapper info = branchService.getBranchAuthoringInfoWrapper(branchPath);
			if (info.codeSystem() != null) {
				return info.codeSystem().getShortName();
			}
		} catch (Exception e) {
			logger.debug("Could not resolve code system for branch {}", branchPath, e);
		}
		return null;
	}

	private String resolveClassificationDeepLink(Notification notification) {
		if (!Strings.isNullOrEmpty(notification.getTask())) {
			return taskPath(notification.getProject(), notification.getTask(), "classify");
		}
		if (!Strings.isNullOrEmpty(notification.getProject())) {
			return "/" + PROJECT + "/" + notification.getProject();
		}
		String codeSystemShortName = resolveCodeSystemShortName(notification.getBranchPath());
		return codeSystemShortName != null ? "/codesystem/" + codeSystemShortName : null;
	}

	private String resolveValidationDeepLink(Notification notification) {
		if (!Strings.isNullOrEmpty(notification.getTask())) {
			return taskPath(notification.getProject(), notification.getTask(), "validate");
		}
		if (!Strings.isNullOrEmpty(notification.getProject())) {
			return "/" + PROJECT + "/" + notification.getProject();
		}
		String codeSystemShortName = resolveCodeSystemShortName(notification.getBranchPath());
		return codeSystemShortName != null ? "/codesystem/" + codeSystemShortName : null;
	}

	private static String taskPath(String project, String task, String section) {
		return "/tasks/" + project + "/" + task + "/" + section;
	}

	private static boolean isClassificationFailureEvent(String event) {
		return event != null && event.toLowerCase(Locale.ROOT).startsWith("failed");
	}

	private static String capitalizeWords(String text) {
		if (Strings.isNullOrEmpty(text)) {
			return text;
		}
		String[] words = text.toLowerCase(Locale.ROOT).split("\\s+");
		StringBuilder result = new StringBuilder();
		for (String word : words) {
			if (!word.isEmpty()) {
				if (!result.isEmpty()) {
					result.append(' ');
				}
				result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
			}
		}
		return result.toString();
	}

	private final CacheBuilder<Notification, String> userNotificationCacheBuilder = CacheBuilder.newBuilder()
			.expireAfterWrite(1, TimeUnit.MINUTES)
			.removalListener(removalNotification -> {
				Notification notification = removalNotification.getKey();
				String username = removalNotification.getValue();
				synchronized (pendingNotifications) {
					pendingNotifications
							.getOrDefault(username, Collections.emptyList())
							.remove(notification);
				}
			});

	private final class ExpiringNotificationList extends ArrayList<Notification> {

		private final String username;
		private final transient Cache<Notification, String> userNotificationCache = userNotificationCacheBuilder.build();

		ExpiringNotificationList(String username) {
			this.username = username;
		}

		@Override
		public boolean add(Notification notification) {
			userNotificationCache.put(notification, username);
			return super.add(notification);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof ExpiringNotificationList that)) {
				return false;
			}
			return Objects.equals(username, that.username) && super.equals(o);
		}

		@Override
		public int hashCode() {
			return Objects.hash(super.hashCode(), username);
		}

	}

}
