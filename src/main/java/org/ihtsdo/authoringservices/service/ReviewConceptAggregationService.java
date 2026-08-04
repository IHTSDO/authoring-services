package org.ihtsdo.authoringservices.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.ihtsdo.authoringservices.domain.ReviewConcept;
import org.ihtsdo.authoringservices.domain.ReviewConceptAggregation;
import org.ihtsdo.authoringservices.domain.ReviewConceptAggregation.AggregatedReviewConcept;
import org.ihtsdo.authoringservices.domain.ReviewConceptAggregation.FeedbackItem;
import org.ihtsdo.authoringservices.entity.ReviewMessage;
import org.ihtsdo.authoringservices.service.client.TraceabilityClient;
import org.ihtsdo.authoringservices.service.client.TraceabilityClientFactory;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ConceptMiniPojo;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aggregates traceability concept changes, Snowstorm concept terms, and reviewer feedback
 * into a single review concepts response (server-side port of authoring-ui getLatestReview).
 */
@Service
public class ReviewConceptAggregationService {

	private static final String CONTENT_CHANGE = "CONTENT_CHANGE";
	private static final String CLASSIFICATION_SAVE = "CLASSIFICATION_SAVE";
	private static final String INFERRED_RELATIONSHIP = "INFERRED_RELATIONSHIP";
	private static final String REVIEWED_LIST_PANEL = "reviewed-list";
	private static final String SHARED = "SHARED";
	private static final int CONCEPT_BATCH_SIZE = 50;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final BranchService branchService;
	private final TraceabilityClientFactory traceabilityClientFactory;
	private final SnowstormRestClientFactory snowstormRestClientFactory;
	private final ReviewService reviewService;
	private final UiStateService uiStateService;

	public ReviewConceptAggregationService(BranchService branchService,
			TraceabilityClientFactory traceabilityClientFactory,
			SnowstormRestClientFactory snowstormRestClientFactory,
			ReviewService reviewService, UiStateService uiStateService) {
		this.branchService = branchService;
		this.traceabilityClientFactory = traceabilityClientFactory;
		this.snowstormRestClientFactory = snowstormRestClientFactory;
		this.reviewService = reviewService;
		this.uiStateService = uiStateService;
	}

	public ReviewConceptAggregation getAggregatedReviewConcepts(String projectKey, String taskKey,
			String username, String acceptLanguage) throws BusinessServiceException {
		String branchPath = branchService.getTaskBranchPathUsingCache(projectKey, taskKey);
		TraceabilityClient.ActivitiesPage activities = fetchActivities(branchPath);

		ChangedConceptSets changed = splitConceptChanges(activities);
		Set<String> allConceptIds = new HashSet<>();
		allConceptIds.addAll(changed.contentConceptIds());
		allConceptIds.addAll(changed.classificationConceptIds());

		Map<String, ConceptTerms> termsById = fetchConceptTerms(branchPath, allConceptIds, acceptLanguage);
		Map<String, List<FeedbackItem>> feedbackById = mapFeedback(projectKey, taskKey, username);
		Set<String> reviewedIds = loadReviewedConceptIds(projectKey, taskKey);

		List<AggregatedReviewConcept> concepts = new ArrayList<>();
		for (String conceptId : changed.contentConceptIds()) {
			concepts.add(buildConcept(conceptId, ReviewConceptAggregation.CHANGE_TYPE_CONTENT,
					termsById, feedbackById, reviewedIds));
		}
		for (String conceptId : changed.classificationConceptIds()) {
			concepts.add(buildConcept(conceptId, ReviewConceptAggregation.CHANGE_TYPE_CLASSIFICATION,
					termsById, feedbackById, reviewedIds));
		}

		int totalReviewed = (int) concepts.stream().filter(AggregatedReviewConcept::isReviewed).count();
		ReviewConceptAggregation aggregation = new ReviewConceptAggregation();
		aggregation.setConcepts(concepts);
		aggregation.setTotalChangedConcepts(concepts.size());
		aggregation.setTotalReviewed(totalReviewed);
		return aggregation;
	}

	static ChangedConceptSets splitConceptChanges(TraceabilityClient.ActivitiesPage activities) {
		LinkedHashMap<String, Date> contentConcepts = new LinkedHashMap<>();
		LinkedHashMap<String, Date> classifiedConcepts = new LinkedHashMap<>();

		if (activities == null || activities.getContent() == null) {
			return new ChangedConceptSets(List.copyOf(contentConcepts.keySet()), List.copyOf(classifiedConcepts.keySet()));
		}

		for (TraceabilityClient.Activity change : activities.getContent()) {
			if (change == null || change.getConceptChanges() == null) {
				continue;
			}
			String activityType = change.getActivityType();
			Date commitDate = change.getCommitDate();

			if (CONTENT_CHANGE.equals(activityType)) {
				for (TraceabilityClient.ConceptChange concept : change.getConceptChanges()) {
					processContentChange(concept, commitDate, contentConcepts, classifiedConcepts);
				}
			} else if (CLASSIFICATION_SAVE.equals(activityType)) {
				for (TraceabilityClient.ConceptChange concept : change.getConceptChanges()) {
					processClassificationSave(concept, commitDate, classifiedConcepts);
				}
			}
		}

		// Exclude stated edits from classification list (mirrors reviewService.js)
		classifiedConcepts.keySet().removeAll(contentConcepts.keySet());

		return new ChangedConceptSets(List.copyOf(contentConcepts.keySet()), List.copyOf(classifiedConcepts.keySet()));
	}

	private static void processContentChange(TraceabilityClient.ConceptChange concept, Date commitDate,
			LinkedHashMap<String, Date> contentConcepts, LinkedHashMap<String, Date> classifiedConcepts) {
		String conceptId = concept.getConceptIdAsString();
		if (!StringUtils.hasLength(conceptId)) {
			return;
		}
		boolean hasNonInferred = hasComponentSubType(concept, false);
		boolean hasInferred = hasComponentSubType(concept, true);

		if (!contentConcepts.containsKey(conceptId) && hasNonInferred) {
			contentConcepts.put(conceptId, commitDate);
		} else if (!classifiedConcepts.containsKey(conceptId) && hasInferred) {
			classifiedConcepts.put(conceptId, commitDate);
		} else if (hasNonInferred) {
			contentConcepts.put(conceptId, commitDate);
		}
	}

	private static void processClassificationSave(TraceabilityClient.ConceptChange concept, Date commitDate,
			LinkedHashMap<String, Date> classifiedConcepts) {
		String conceptId = concept.getConceptIdAsString();
		if (!StringUtils.hasLength(conceptId)) {
			return;
		}
		classifiedConcepts.put(conceptId, commitDate);
	}

	private static boolean hasComponentSubType(TraceabilityClient.ConceptChange concept, boolean inferred) {
		if (concept.getComponentChanges() == null) {
			return false;
		}
		for (TraceabilityClient.ComponentChange componentChange : concept.getComponentChanges()) {
			boolean isInferred = INFERRED_RELATIONSHIP.equals(componentChange.getComponentSubType());
			if (inferred == isInferred) {
				return true;
			}
		}
		return false;
	}

	private AggregatedReviewConcept buildConcept(String conceptId, String changeType,
			Map<String, ConceptTerms> termsById, Map<String, List<FeedbackItem>> feedbackById,
			Set<String> reviewedIds) {
		AggregatedReviewConcept concept = new AggregatedReviewConcept();
		concept.setConceptId(conceptId);
		concept.setChangeType(changeType);
		ConceptTerms terms = termsById.get(conceptId);
		if (terms != null) {
			concept.setFsn(terms.fsn());
			concept.setPt(terms.pt());
		}
		concept.setFeedback(feedbackById.getOrDefault(conceptId, List.of()));
		concept.setReviewed(reviewedIds.contains(conceptId));
		return concept;
	}

	private Map<String, ConceptTerms> fetchConceptTerms(String branchPath, Set<String> conceptIds, String acceptLanguage) {
		Map<String, ConceptTerms> termsById = new HashMap<>();
		if (conceptIds.isEmpty()) {
			return termsById;
		}
		SnowstormRestClient client = snowstormRestClientFactory.getClient();
		List<String> idList = new ArrayList<>(conceptIds);
		try {
			for (int i = 0; i < idList.size(); i += CONCEPT_BATCH_SIZE) {
				List<String> batch = idList.subList(i, Math.min(i + CONCEPT_BATCH_SIZE, idList.size()));
				addConceptTerms(termsById, client.getConceptMinis(branchPath, batch, batch.size(), acceptLanguage));
			}
		} catch (RestClientException e) {
			logger.warn("Failed to retrieve concept terms for branch {}: {}", branchPath, e.getMessage());
		}
		return termsById;
	}

	private void addConceptTerms(Map<String, ConceptTerms> termsById, Set<ConceptMiniPojo> concepts) {
		if (concepts == null) {
			return;
		}
		for (ConceptMiniPojo concept : concepts) {
			ConceptTerms terms = toConceptTerms(concept);
			if (terms != null) {
				termsById.put(concept.getConceptId(), terms);
			}
		}
	}

	private ConceptTerms toConceptTerms(ConceptMiniPojo concept) {
		if (concept == null || !StringUtils.hasLength(concept.getConceptId())) {
			return null;
		}
		String fsn = concept.getFsn() != null ? concept.getFsn().getTerm() : concept.getFsnTerm();
		String pt = concept.getPt() != null ? concept.getPt().getTerm() : null;
		return new ConceptTerms(fsn, pt);
	}

	private Map<String, List<FeedbackItem>> mapFeedback(String projectKey, String taskKey, String username) {
		Map<String, List<FeedbackItem>> feedbackById = new HashMap<>();
		List<ReviewConcept> reviewConcepts = reviewService.retrieveTaskReviewConceptDetails(projectKey, taskKey, username);
		for (ReviewConcept reviewConcept : reviewConcepts) {
			List<FeedbackItem> items = new ArrayList<>();
			if (reviewConcept.getMessages() != null) {
				for (ReviewMessage message : reviewConcept.getMessages()) {
					items.add(new FeedbackItem(message.getMessageHtml(), message.getFromUsername(), message.getCreationDate()));
				}
			}
			feedbackById.put(reviewConcept.getId(), items);
		}
		return feedbackById;
	}

	private Set<String> loadReviewedConceptIds(String projectKey, String taskKey) {
		Set<String> reviewedIds = new HashSet<>();
		try {
			JsonNode reviewedList = uiStateService.retrieveTaskPanelStateWithoutThrowingResourceNotFoundException(
					projectKey, taskKey, SHARED, REVIEWED_LIST_PANEL);
			collectReviewedIds(reviewedList, reviewedIds);
		} catch (IOException e) {
			logger.error("Failed to read reviewed-list for task {}/{}: {}", projectKey, taskKey, e.getMessage());
		}
		return reviewedIds;
	}

	private void collectReviewedIds(JsonNode reviewedList, Set<String> reviewedIds) {
		if (reviewedList == null) {
			return;
		}
		JsonNode idArray = reviewedList.isArray() ? reviewedList : reviewedList.get("conceptIds");
		if (idArray == null || !idArray.isArray()) {
			return;
		}
		for (JsonNode node : idArray) {
			if (node.isTextual() || node.isNumber()) {
				reviewedIds.add(node.asText());
			}
		}
	}

	private TraceabilityClient.ActivitiesPage fetchActivities(String branchPath) {
		TraceabilityClient client = traceabilityClientFactory.getClient();
		if (client == null) {
			logger.debug("Traceability URL not configured; assuming no branch activities");
			TraceabilityClient.ActivitiesPage empty = new TraceabilityClient.ActivitiesPage();
			empty.setContent(List.of());
			empty.setNumberOfElements(0);
			return empty;
		}
		return client.getActivitiesForBranch(branchPath);
	}

	record ChangedConceptSets(List<String> contentConceptIds, List<String> classificationConceptIds) {
	}

	record ConceptTerms(String fsn, String pt) {
	}
}
