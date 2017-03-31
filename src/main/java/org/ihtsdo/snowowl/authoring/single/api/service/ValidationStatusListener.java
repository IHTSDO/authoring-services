package org.ihtsdo.snowowl.authoring.single.api.service;

import org.ihtsdo.otf.jms.MessagingHelper;
import org.ihtsdo.snowowl.authoring.single.api.pojo.EntityType;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import javax.jms.JMSException;
import javax.jms.TextMessage;

import static org.ihtsdo.snowowl.authoring.single.api.service.ValidationService.*;

@Component
public class ValidationStatusListener {

	@Autowired
	private ValidationService validationService;

	@Autowired
	private NotificationService notificationService;

	private Logger logger = LoggerFactory.getLogger(getClass());

	@SuppressWarnings("unused")
	@JmsListener(destination = "${orchestration.name}" + "." + ValidationService.VALIDATION_RESPONSE_QUEUE)
	public void receiveValidationEvent(TextMessage message) {
		try {
			if (!MessagingHelper.isError(message)) {
				logger.info("receiveValidationEvent {}", message);
				final String validationStatus = message.getStringProperty(STATUS);

				// Update cache
				String path = message.getStringProperty(MessagingHelper.REQUEST_PROPERTY_NAME_PREFIX + PATH);
				logger.info("Path '{}'", path);
				logger.info("Status '{}'", validationStatus);

				validationService.updateValidationStatusCache(path, validationStatus);

				// Notify user
				notificationService.queueNotification(
						message.getStringProperty(MessagingHelper.REQUEST_PROPERTY_NAME_PREFIX + USERNAME),
						new Notification(
								message.getStringProperty(MessagingHelper.REQUEST_PROPERTY_NAME_PREFIX + PROJECT),
								message.getStringProperty(MessagingHelper.REQUEST_PROPERTY_NAME_PREFIX + TASK),
								EntityType.Validation,
								validationStatus));
			} else {
				logger.error("receiveValidationEvent response with error {}", message);
			}
		} catch (JMSException e) {
			logger.error("Failed to handle validation event message.", e);
		}
	}

}
