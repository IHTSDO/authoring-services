package org.ihtsdo.authoringservices.configuration;

import org.ihtsdo.authoringservices.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfiguration implements WebSocketMessageBrokerConfigurer {

	@Autowired
	private NotificationService notificationService;

	@Override
	public void configureMessageBroker(MessageBrokerRegistry brokerRegistry) {
		brokerRegistry.enableSimpleBroker("/topic");
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry stompEndpointRegistry) {
		stompEndpointRegistry
				.addEndpoint("/authoring-services-websocket")
				.setAllowedOrigins("*")
				.withSockJS()
				.setSupressCors(true);
	}

	@Scheduled(cron = "*/10 * * * * *")
	public void autoSendingNotifications() {
		notificationService.sendNotification();
	}
	
}
