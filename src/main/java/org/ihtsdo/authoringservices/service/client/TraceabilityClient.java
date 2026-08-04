package org.ihtsdo.authoringservices.service.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.ihtsdo.otf.rest.client.ExpressiveErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class TraceabilityClient {

	private static final Logger logger = LoggerFactory.getLogger(TraceabilityClient.class);
	private static final int PAGE_SIZE = 500;

	private final RestTemplate restTemplate;

	public TraceabilityClient(String traceabilityUrl, String authToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("Cookie", authToken);
		headers.setContentType(MediaType.APPLICATION_JSON);
		restTemplate = new RestTemplateBuilder()
				.rootUri(traceabilityUrl)
				.errorHandler(new ExpressiveErrorHandler())
				.build();
		restTemplate.getInterceptors().add((request, body, execution) -> {
			request.getHeaders().addAll(headers);
			return execution.execute(request, body);
		});
	}

	/**
	 * Fetches all activities for a branch, paging until exhausted.
	 * Returns an empty page on failure (matching frontend behaviour of treating errors as no content).
	 */
	public ActivitiesPage getActivitiesForBranch(String branchPath) {
		try {
			ActivitiesPage aggregate = null;
			int page = 0;
			boolean hasMore;
			do {
				String uri = UriComponentsBuilder.fromPath("/activities")
						.queryParam("onBranch", branchPath)
						.queryParam("size", PAGE_SIZE)
						.queryParam("page", page)
						.build()
						.toUriString();
				ActivitiesPage response = restTemplate.getForObject(uri, ActivitiesPage.class);
				if (response == null) {
					break;
				}
				if (aggregate == null) {
					aggregate = response;
					if (aggregate.getContent() == null) {
						aggregate.setContent(new ArrayList<>());
					}
				} else if (response.getContent() != null) {
					aggregate.getContent().addAll(response.getContent());
				}
				if (response.getTotalPages() != null) {
					hasMore = (page + 1) < response.getTotalPages();
				} else {
					hasMore = response.getContent() != null && response.getContent().size() == PAGE_SIZE;
				}
				page++;
			} while (hasMore);
			return aggregate != null ? aggregate : emptyPage();
		} catch (RestClientException e) {
			logger.warn("Failed to retrieve traceability for branch {}: {}", branchPath, e.getMessage());
			return emptyPage();
		}
	}

	private static ActivitiesPage emptyPage() {
		ActivitiesPage page = new ActivitiesPage();
		page.setContent(Collections.emptyList());
		page.setNumberOfElements(0);
		page.setTotalPages(0);
		return page;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class ActivitiesPage {
		private List<Activity> content = new ArrayList<>();
		private Integer numberOfElements;
		private Integer totalPages;

		public List<Activity> getContent() {
			return content;
		}

		public void setContent(List<Activity> content) {
			this.content = content;
		}

		public Integer getNumberOfElements() {
			return numberOfElements;
		}

		public void setNumberOfElements(Integer numberOfElements) {
			this.numberOfElements = numberOfElements;
		}

		public Integer getTotalPages() {
			return totalPages;
		}

		public void setTotalPages(Integer totalPages) {
			this.totalPages = totalPages;
		}

		public boolean hasActivities() {
			return (numberOfElements != null && numberOfElements > 0)
					|| (content != null && !content.isEmpty());
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Activity {
		private String activityType;
		private Date commitDate;
		private List<ConceptChange> conceptChanges = new ArrayList<>();

		public String getActivityType() {
			return activityType;
		}

		public void setActivityType(String activityType) {
			this.activityType = activityType;
		}

		public Date getCommitDate() {
			return commitDate;
		}

		public void setCommitDate(Date commitDate) {
			this.commitDate = commitDate;
		}

		public List<ConceptChange> getConceptChanges() {
			return conceptChanges;
		}

		public void setConceptChanges(List<ConceptChange> conceptChanges) {
			this.conceptChanges = conceptChanges != null ? conceptChanges : new ArrayList<>();
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class ConceptChange {
		private Object conceptId;
		private List<ComponentChange> componentChanges = new ArrayList<>();

		public String getConceptIdAsString() {
			return conceptId != null ? conceptId.toString() : null;
		}

		public Object getConceptId() {
			return conceptId;
		}

		public void setConceptId(Object conceptId) {
			this.conceptId = conceptId;
		}

		public List<ComponentChange> getComponentChanges() {
			return componentChanges;
		}

		public void setComponentChanges(List<ComponentChange> componentChanges) {
			this.componentChanges = componentChanges != null ? componentChanges : new ArrayList<>();
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class ComponentChange {
		private String componentSubType;

		public String getComponentSubType() {
			return componentSubType;
		}

		public void setComponentSubType(String componentSubType) {
			this.componentSubType = componentSubType;
		}
	}
}
