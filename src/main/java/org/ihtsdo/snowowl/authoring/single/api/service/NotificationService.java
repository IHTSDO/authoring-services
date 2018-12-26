package org.ihtsdo.snowowl.authoring.single.api.service;

import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class NotificationService {

	@Autowired
	private TaskService taskService;

	@Autowired
	private SimpMessagingTemplate simpMessagingTemplate;

	private final Map<String, List<Notification>> pendingNotifications = new HashMap<>();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Deprecated
	public List<Notification> retrieveNewNotifications(String username) {
		if (pendingNotifications.containsKey(username)) {
			synchronized (pendingNotifications) {
				return pendingNotifications.remove(username);
			}
		}
		return null;
	}

	public Notification queueNotification(String username, Notification notification) {
		final String projectKey = notification.getProject();
		if (!Strings.isNullOrEmpty(projectKey)) {
			try {
				notification.setBranchPath(taskService.getBranchPathUsingCache(projectKey, notification.getTask()));
			} catch (BusinessServiceException e) {
				logger.error("Failed to retrieve project base for {}", projectKey);
			}
		}
		logger.info("Notification for user {} - '{}'", username, notification);
		synchronized (pendingNotifications) {
			if (!pendingNotifications.containsKey(username)) {
				pendingNotifications.put(username, new ExpiringNotificationList(username));
			}
			pendingNotifications.get(username).add(notification);
		}
		simpMessagingTemplate.convertAndSend("/topic/user/" + username + "/notifications", notification);
		return notification;
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
		private final Cache<Notification, String> userNotificationCache = userNotificationCacheBuilder.build();

		ExpiringNotificationList(String username) {
			this.username = username;
		}

		@Override
		public boolean add(Notification notification) {
			userNotificationCache.put(notification, username);
			return super.add(notification);
		}

	}

}
