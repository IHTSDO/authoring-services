package org.ihtsdo.authoringservices.service.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.logging.log4j.util.Strings;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Set;

@Service
public class AuthoringAcceptanceGatewayClient {

	private RestTemplate restTemplate;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public AuthoringAcceptanceGatewayClient(@Value("${aag.url:}") String aagUrl) {
		if (!Strings.isEmpty(aagUrl)) {
			restTemplate = new RestTemplateBuilder().baseUri(aagUrl).build();
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

	/**
	 * Fetch branch SAC criteria items from AAG.
	 * Returns null when AAG is not configured, criteria are missing (404), or the call fails —
	 * matching authoring-ui behaviour of treating missing SAC as no open criteria.
	 */
	public BranchAcceptance getBranchAcceptance(String branchPath, boolean matchAuthorFlags) {
		if (restTemplate == null) {
			logger.debug("AAG url not configured. Skipping branch SAC lookup for {}.", branchPath);
			return null;
		}
		try {
			String encodedBranch = branchPath.replace("/", "|");
			String uri = UriComponentsBuilder.fromPath("/acceptance/{branch}")
					.queryParam("matchAuthorFlags", matchAuthorFlags)
					.buildAndExpand(encodedBranch)
					.toUriString();

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			String authToken = SecurityUtil.getAuthenticationToken();
			if (!Strings.isEmpty(authToken)) {
				headers.add(HttpHeaders.COOKIE, authToken);
			}

			ResponseEntity<BranchAcceptance> response = restTemplate.exchange(
					uri, HttpMethod.GET, new HttpEntity<>(headers), BranchAcceptance.class);
			return response.getBody();
		} catch (HttpClientErrorException.NotFound e) {
			logger.debug("No acceptance criteria for branch {}", branchPath);
			return null;
		} catch (RestClientException e) {
			logger.warn("Failed to retrieve branch SAC for {}: {}", branchPath, e.getMessage());
			return null;
		}
	}

	/**
	 * Returns true when every TASK-level criteria item is complete, or when no SAC is configured.
	 * Mirrors authoring-ui taskDetail.sacSignedOff().
	 */
	public boolean areTaskSacSignedOff(String branchPath) {
		BranchAcceptance acceptance = getBranchAcceptance(branchPath, true);
		if (acceptance == null || acceptance.getCriteriaItems() == null || acceptance.getCriteriaItems().isEmpty()) {
			return true;
		}
		for (CriteriaItem item : acceptance.getCriteriaItems()) {
			if ("TASK".equalsIgnoreCase(item.getAuthoringLevel()) && !item.isComplete()) {
				return false;
			}
		}
		return true;
	}

	private record ValidationCompleteRequest(String branchPath, String validationStatus, String reportUrl) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class BranchAcceptance {
		private String branchPath;
		private Set<CriteriaItem> criteriaItems;

		public String getBranchPath() {
			return branchPath;
		}

		public void setBranchPath(String branchPath) {
			this.branchPath = branchPath;
		}

		public Set<CriteriaItem> getCriteriaItems() {
			return criteriaItems;
		}

		public void setCriteriaItems(Set<CriteriaItem> criteriaItems) {
			this.criteriaItems = criteriaItems;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class CriteriaItem {
		private String id;
		private String authoringLevel;
		private boolean complete;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getAuthoringLevel() {
			return authoringLevel;
		}

		public void setAuthoringLevel(String authoringLevel) {
			this.authoringLevel = authoringLevel;
		}

		public boolean isComplete() {
			return complete;
		}

		public void setComplete(boolean complete) {
			this.complete = complete;
		}
	}
}
