package org.ihtsdo.authoringservices.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@ConditionalOnProperty(name = "authoring.notifications.websocket.enabled", havingValue = "true", matchIfMissing = true)
public class WebSocketConfiguration implements WebSocketMessageBrokerConfigurer {

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
				.setSuppressCors(true);
	}
}
