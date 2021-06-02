package org.ihtsdo.authoringservices.service;

import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import org.apache.commons.collections.CollectionUtils;
import org.ihtsdo.authoringservices.service.monitor.MonitorService;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.authoringservices.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpSubscriptionMatcher;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class NotificationService {

	@Autowired
	private TaskService taskService;

	@Autowired
	private SimpMessagingTemplate simpMessagingTemplate;

	@Autowired
	private SimpUserRegistry simpUserRegistry;

	@Autowired
	private MonitorService monitorService;

	private final Map<String, List<Notification>> pendingNotifications = new HashMap<>();

	private final Logger logger = LoggerFactory.getLogger(getClass());

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
			sendNotification();
		}

		return notification;
	}

	public void sendNotification() {
		if (logger.isDebugEnabled()) {
			logger.debug("Current users: {}", simpUserRegistry.getUsers());
		}

		Set<SimpUser> currentUsers = simpUserRegistry.getUsers();
		for (SimpUser simpUser : currentUsers) {
			String username =  simpUser.getName();
			monitorService.keepMonitorsAlive(username);

			SimpSubscriptionMatcher simpSubscriptionMatcher = new SimpSubscriptionMatcher() {
				@Override
				public boolean match(SimpSubscription subscription) {
					return subscription.getDestination().equals("/topic/user/" + username + "/notifications");
				}
			};
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
					simpMessagingTemplate.convertAndSend("/topic/user/" + username + "/notifications", notifications.get(notifications.size() - 1));
				}
			}
		}
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
