package org.ihtsdo.authoringservices.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang.StringUtils;
import org.ihtsdo.authoringservices.domain.EntityType;
import org.ihtsdo.authoringservices.domain.Notification;
import org.ihtsdo.authoringservices.domain.ValidationJobStatus;
import org.ihtsdo.authoringservices.entity.Validation;
import org.ihtsdo.authoringservices.entity.ValidationFailureMessagesConverter;
import org.ihtsdo.authoringservices.repository.ValidationRepository;
import org.ihtsdo.authoringservices.service.client.AuthoringAcceptanceGatewayClient;
import org.ihtsdo.authoringservices.service.client.RVFClientFactory;
import org.ihtsdo.otf.jms.MessagingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;

import jakarta.jms.JMSException;
import jakarta.jms.TextMessage;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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

	@Autowired
	private RVFClientFactory rvfClientFactory;

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
					try {
						Map<String, String> newPropertyValues = new HashMap<>();
						newPropertyValues.put(ValidationService.VALIDATION_STATUS, state);

						if (ValidationJobStatus.COMPLETED.name().equalsIgnoreCase(state) || ValidationJobStatus.FAILED.name().equalsIgnoreCase(state)) {
							newPropertyValues.put(ValidationService.VALIDATION_END_TIMESTAMP, String.valueOf((new Date()).getTime()));

							if (ValidationJobStatus.FAILED.name().equalsIgnoreCase(state)) {
								populateFailureMessages(validation, newPropertyValues, objectMapper, username, authenticationToken);
							}

							validationService.updateValidationCache(validation.getBranchPath(), newPropertyValues);

							// Notify AAG
							if (ValidationJobStatus.COMPLETED.name().equalsIgnoreCase(state)) {
								aagClient.validationComplete(validation.getBranchPath(), state, validation.getReportUrl(), authenticationToken);
							}
						} else {
							validationService.updateValidationCache(validation.getBranchPath(), newPropertyValues);
						}
					} finally {
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

	private void populateFailureMessages(Validation validation, Map<String, String> newPropertyValues, ObjectMapper objectMapper, String username, String authenticationToken) {
		try {
			if (validation.getReportUrl() == null) {
				logger.warn("Validation {} failed but report URL is null, cannot populate failure messages.", validation.getId());
				return;
			}
			PreAuthenticatedAuthenticationToken decoratedAuthentication = new PreAuthenticatedAuthenticationToken(username, authenticationToken);
			SecurityContextHolder.getContext().setAuthentication(decoratedAuthentication);
			String reportJson = rvfClientFactory.getClient().getValidationReport(validation.getReportUrl());
			if (org.springframework.util.StringUtils.hasLength(reportJson)) {
				JsonNode root = objectMapper.readTree(reportJson);
				JsonNode failureMessagesNode = root.path("rvfValidationResult")
						.path("failureMessages");
				if (failureMessagesNode.isArray()) {
					List<String> messages = new ArrayList<>();
					for (JsonNode item : failureMessagesNode) {
						String message = item.asText("");
						if (!message.isBlank()) {
							messages.add(message);
						}
					}
					if (!messages.isEmpty()) {
						newPropertyValues.put(ValidationService.FAILURE_MESSAGES,
								String.join(ValidationFailureMessagesConverter.DELIMITER, messages));
					}
				}
			}
		} catch (Exception e) {
			logger.error("Failed to populate failure messages from RVF report for validation {}", validation.getId(), e);
		}
	}

}
