package org.ihtsdo.authoringservices.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.ihtsdo.authoringservices.domain.EntityType;
import org.ihtsdo.authoringservices.domain.Notification;
import org.ihtsdo.authoringservices.service.client.AuthoringAcceptanceGatewayClient;
import org.ihtsdo.otf.jms.MessagingHelper;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import javax.jms.JMSException;
import javax.jms.TextMessage;

import static org.ihtsdo.authoringservices.service.ValidationService.*;

@Component
public class ValidationStatusListener {

	@Autowired
	private ValidationService validationService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private AuthoringAcceptanceGatewayClient aagClient;

	private static final String X_AUTH_TOKEN = "X-AUTH-token";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@SuppressWarnings("unused")
	@JmsListener(destination = "${orchestration.name}" + "." + ValidationService.VALIDATION_RESPONSE_QUEUE)
	public void receiveValidationEvent(TextMessage message) {
		try {
			if (!MessagingHelper.isError(message)) {
				logger.info("receiveValidationEvent {}", message);
				final String validationStatus = message.getStringProperty(STATUS);
				final String reportUrl = message.getStringProperty(REPORT_URL);

				// Update cache
				String path = message.getStringProperty(MessagingHelper.REQUEST_PROPERTY_NAME_PREFIX + PATH);
				logger.info("Path '{}', status '{}', reportUrl '{}'", path, validationStatus, reportUrl);

				try {
					validationService.updateValidationStatusCache(path, validationStatus, reportUrl);
				} catch (BusinessServiceException e) {
					logger.error("Failed to update validation status to Cache.", e);
				}

				// Notify user
				final String projectId = message.getStringProperty(MessagingHelper.REQUEST_PROPERTY_NAME_PREFIX + PROJECT);
				final String taskId = message.getStringProperty(MessagingHelper.REQUEST_PROPERTY_NAME_PREFIX + TASK);
				notificationService.queueNotification(
						message.getStringProperty(MessagingHelper.REQUEST_PROPERTY_NAME_PREFIX + USERNAME),
						new Notification(
								projectId,
								taskId,
								EntityType.Validation,
								validationStatus));

				// Notify AAG
				final String authToken = message.getStringProperty(X_AUTH_TOKEN);
				aagClient.validationComplete(path, validationStatus, reportUrl, authToken);
			} else {
				logger.error("receiveValidationEvent response with error {}", message);
			}
		} catch (JMSException e) {
			logger.error("Failed to handle validation event message.", e);
		}
	}

}
