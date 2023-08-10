package org.ihtsdo.authoringservices.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.ihtsdo.otf.jms.MessagingHelper;
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
public class DailyBuildRvfResponseListener {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	private ValidationService validationService;

	@SuppressWarnings("unused")
	@JmsListener(destination = "${srs.jms.queue.prefix}.daily-build-rvf-response-queue")
	public void receiveDailyBuildRvfResponseEvent(TextMessage textMessage) {
		try {
			if (!MessagingHelper.isError(textMessage)) {
				logger.info("receiveDailyBuildRvfResponseEvent {}", textMessage);
				ObjectMapper objectMapper =  new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
						.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
				final Map<String, Object> message = objectMapper.readValue(textMessage.getText(), Map.class);

				final String branchPath = (String) message.get("branchPath");
				final String rvfURL = (String) message.get("rvfURL");
				Map<String, String> newPropertyValues = new HashMap<>();
				newPropertyValues.put(ValidationService.DAILY_BUILD_REPORT_URL, rvfURL);
				validationService.updateValidationCache(branchPath, newPropertyValues);
			} else {
				logger.error("receiveDailyBuildRvfResponseEvent response with error {}", textMessage);
			}
		} catch (JMSException | JsonProcessingException e) {
			logger.error("Failed to handle Daily Build Rvf Response event message.", e);
		}
	}
}
