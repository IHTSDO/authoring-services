package org.ihtsdo.authoringservices.configuration;

import org.ihtsdo.authoringservices.service.NotificationService;
import org.ihtsdo.authoringservices.service.monitor.MonitorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Set;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfiguration implements WebSocketMessageBrokerConfigurer {

	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	@Autowired
	private SimpUserRegistry simpUserRegistry;
	
	@Autowired
	private NotificationService notificationService;

	@Autowired
	private MonitorService monitorService;
	
	@Override
	public void configureMessageBroker(MessageBrokerRegistry brokerRegistry) {
		brokerRegistry.enableSimpleBroker("/topic");
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry stompEndpointRegistry) {
		stompEndpointRegistry.addEndpoint("/authoring-services-websocket").withSockJS();
	}

	@Scheduled(cron = "*/10 * * * * *")
	public void readWebSocketConnections() {
		if (logger.isDebugEnabled()) {
			logger.debug("Current users: {}", simpUserRegistry.getUsers());
		}
		
		Set<SimpUser> currentUsers = simpUserRegistry.getUsers();
		for (SimpUser simpUser : currentUsers) {
			String username =  simpUser.getName();
			monitorService.keepMonitorsAlive(username);
			notificationService.sendNotification(username);
		}
	}
	
}
