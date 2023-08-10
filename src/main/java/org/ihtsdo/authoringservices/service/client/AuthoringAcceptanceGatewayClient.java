package org.ihtsdo.authoringservices.service.client;

import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class AuthoringAcceptanceGatewayClient {

	private RestTemplate restTemplate;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public AuthoringAcceptanceGatewayClient(@Value("${aag.url}") String aagUrl) {
		if (!Strings.isEmpty(aagUrl)) {
			restTemplate = new RestTemplateBuilder().rootUri(aagUrl).build();
		}
	}

	public void validationComplete(String branchPath, String validationStatus, String reportUrl, String authToken) {
		if (restTemplate == null) {
			logger.debug("AAG url not configured. Not sending notification.");
			return;
		}
		try {
			final HttpHeaders httpHeaders = new HttpHeaders();
			httpHeaders.setContentType(MediaType.APPLICATION_JSON);
			httpHeaders.add(HttpHeaders.COOKIE, authToken);

			restTemplate.postForEntity("/integration/validation-complete",
					new HttpEntity<>(new ValidationCompleteRequest(branchPath, validationStatus, reportUrl), httpHeaders), Void.class);
		} catch (RestClientException e) {
			logger.error("Failed to notify the AAG of a validation completion for branch:{}", branchPath, e);
		}
	}

	private record ValidationCompleteRequest(String branchPath, String validationStatus, String reportUrl) {


	}
}
