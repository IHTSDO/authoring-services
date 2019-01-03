package org.ihtsdo.snowowl.authoring.single.api.configuration;

import java.util.Set;

import org.ihtsdo.snowowl.authoring.single.api.service.NotificationService;
import org.ihtsdo.snowowl.authoring.single.api.service.monitor.MonitorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfiguration extends AbstractWebSocketMessageBrokerConfigurer {

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
		stompEndpointRegistry.addEndpoint("/authoring-services-websocket").setAllowedOrigins("*").withSockJS();
	}

	@Scheduled(cron = "*/10 * * * * *")
	public void readWebSocketConnections() {
		logger.info("Current users: {}", simpUserRegistry.getUsers());
		Set<SimpUser> currentUsers = simpUserRegistry.getUsers();
		for (SimpUser simpUser : currentUsers) {
			String username =  simpUser.getName();
			monitorService.keepMonitorsAlive(username);
			notificationService.sendNotification(username);
		}
	}
	
}
