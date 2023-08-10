package org.ihtsdo.authoringservices.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.lang.StringUtils;
import org.ihtsdo.authoringservices.domain.EntityType;
import org.ihtsdo.authoringservices.domain.Notification;
import org.ihtsdo.authoringservices.domain.ValidationJobStatus;
import org.ihtsdo.authoringservices.entity.Validation;
import org.ihtsdo.authoringservices.repository.ValidationRepository;
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

import java.util.HashMap;
import java.util.Map;

@Component
public class ValidationStatusListener {

	@Autowired
	private ValidationService validationService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private AuthoringAcceptanceGatewayClient aagClient;

	@Autowired
	private ValidationRepository validationRepository;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@SuppressWarnings("unused")
	@JmsListener(destination = "${sca.jms.queue.prefix}." + ValidationService.VALIDATION_RESPONSE_QUEUE)
	public void receiveValidationEvent(TextMessage textMessage) {
		try {
			if (!MessagingHelper.isError(textMessage)) {
				logger.info("receiveValidationEvent {}", textMessage);
				ObjectMapper objectMapper =  new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
						.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
				final Map <String, Object> message = objectMapper.readValue(textMessage.getText(), Map.class);

				final Long runId = (Long) message.get("runId");
				final String username = (String) message.get("username");
				final String authenticationToken = (String) message.get("authenticationToken");
				String state = (String) message.get("state");
				if (StringUtils.isNotEmpty(state)) {
					state = "COMPLETE".equalsIgnoreCase(state) ? ValidationJobStatus.COMPLETED.name() : state;
				}
				Validation validation = validationRepository.findByRunId(runId);
				if (validation != null) {
					Map<String, String> newPropertyValues = new HashMap<>();
					newPropertyValues.put(ValidationService.VALIDATION_STATUS, state);
					validationService.updateValidationCache(validation.getBranchPath(), newPropertyValues);
					if (ValidationJobStatus.COMPLETED.name().equalsIgnoreCase(state) || ValidationJobStatus.FAILED.name().equalsIgnoreCase(state)) {
						// Notify user
						Notification notification = new Notification(
								validation.getProjectKey(),
								validation.getTaskKey(),
								EntityType.Validation,
								state);
						notification.setBranchPath(validation.getBranchPath());
						notificationService.queueNotification(
								username,
								notification);

						// Notify AAG
						if (ValidationJobStatus.COMPLETED.name().equalsIgnoreCase(state)) {
							aagClient.validationComplete(validation.getBranchPath(), state, validation.getReportUrl(), authenticationToken);
						}
					}
				} else {
					logger.error("Error while retrieving validation for run Id {}", runId);
				}
			} else {
				logger.error("receiveValidationEvent response with error {}", textMessage);
			}
		} catch (JMSException | JsonProcessingException e) {
			logger.error("Failed to handle validation event message.", e);
		}
	}

}
