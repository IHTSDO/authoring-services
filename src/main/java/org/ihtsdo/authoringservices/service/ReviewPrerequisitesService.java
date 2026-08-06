package org.ihtsdo.authoringservices.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.ihtsdo.authoringservices.domain.ReviewPrerequisites;
import org.ihtsdo.authoringservices.domain.ReviewPrerequisites.UnsavedConcept;
import org.ihtsdo.authoringservices.service.client.TraceabilityClient;
import org.ihtsdo.authoringservices.service.client.TraceabilityClientFactory;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Branch;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Classification;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ClassificationStatus;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class ReviewPrerequisitesService {

	private static final String MODIFIED_LIST_PANEL = "modified-list";
	private static final String CONCEPT_PANEL_PREFIX = "concept-";
	private static final String CLASSIFICATION_SAVE = "CLASSIFICATION_SAVE";
	private static final String COULD_NOT_DETERMINE_FSN = "Could not determine FSN";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final UiStateService uiStateService;
	private final BranchService branchService;
	private final SnowstormClassificationClient classificationClient;
	private final TraceabilityClientFactory traceabilityClientFactory;
	private final CrsBlockingStateService crsBlockingStateService;

	public ReviewPrerequisitesService(UiStateService uiStateService, BranchService branchService,
			SnowstormClassificationClient classificationClient, TraceabilityClientFactory traceabilityClientFactory,
			CrsBlockingStateService crsBlockingStateService) {
		this.uiStateService = uiStateService;
		this.branchService = branchService;
		this.classificationClient = classificationClient;
		this.traceabilityClientFactory = traceabilityClientFactory;
		this.crsBlockingStateService = crsBlockingStateService;
	}

	public ReviewPrerequisites getReviewPrerequisites(String projectKey, String taskKey, String username)
			throws BusinessServiceException {
		ReviewPrerequisites prerequisites = new ReviewPrerequisites();
		List<String> blockers = new ArrayList<>();

		String branchPath = branchService.getTaskBranchPathUsingCache(projectKey, taskKey);
		Branch branch;
		try {
			branch = branchService.getBranchOrNull(branchPath);
		} catch (ServiceException e) {
			throw new BusinessServiceException("Failed to retrieve branch details for " + branchPath, e);
		}

		TraceabilityClient.ActivitiesPage activities = fetchActivities(branchPath);
		boolean hasUncommittedChanges = activities.hasActivities();
		prerequisites.setHasUncommittedChanges(hasUncommittedChanges);
		if (!hasUncommittedChanges) {
			blockers.add("No content changes found on this task");
		}

		List<UnsavedConcept> unsavedConcepts = collectUnsavedConcepts(projectKey, taskKey, username);
		prerequisites.setUnsavedConcepts(unsavedConcepts);
		for (UnsavedConcept unsavedConcept : unsavedConcepts) {
			blockers.add("Unsaved concept: " + unsavedConcept.conceptId() + " |" + unsavedConcept.fsn() + "|");
		}

		Classification classification = null;
		try {
			classification = classificationClient.getLatestClassification(branchPath);
		} catch (RestClientException e) {
			logger.warn("Failed to retrieve latest classification for {}: {}", branchPath, e.getMessage());
		}

		evaluateClassification(branch, classification, activities, prerequisites, blockers);

		List<String> crsBlockingConcepts = crsBlockingStateService.collectBlockingConcepts(projectKey, taskKey, username)
				.stream()
				.map(concept -> concept.conceptId() + " (Request ID: " + concept.crsRequestId() + ")")
				.toList();
		prerequisites.setCrsBlockingConcepts(crsBlockingConcepts);
		for (String crsBlockingConcept : crsBlockingConcepts) {
			blockers.add("Unsaved CRS concept: " + crsBlockingConcept);
		}

		prerequisites.setBlockers(blockers);
		// Mirrors authoring-ui: hasChangedContent && unsavedConcepts.length === 0
		// && classificationStatuses.length === 0 && unsavedCrsRequests.length === 0
		boolean noClassificationIssues = blockers.stream().noneMatch(ReviewPrerequisitesService::isClassificationBlocker);
		prerequisites.setReadyForReview(hasUncommittedChanges
				&& unsavedConcepts.isEmpty()
				&& noClassificationIssues
				&& crsBlockingConcepts.isEmpty());

		return prerequisites;
	}

	private static boolean isClassificationBlocker(String blocker) {
		return blocker.startsWith("Branch ")
				|| blocker.startsWith("Could Not Retrieve Branch")
				|| blocker.startsWith("Classification ")
				|| blocker.startsWith("Equivalencies ");
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

	private List<UnsavedConcept> collectUnsavedConcepts(String projectKey, String taskKey, String username) {
		List<UnsavedConcept> unsavedConcepts = new ArrayList<>();
		try {
			JsonNode modifiedList = uiStateService.retrieveTaskPanelStateWithoutThrowingResourceNotFoundException(
					projectKey, taskKey, username, MODIFIED_LIST_PANEL);
			if (modifiedList == null || !modifiedList.isArray()) {
				return unsavedConcepts;
			}
			for (JsonNode conceptIdNode : modifiedList) {
				UnsavedConcept unsavedConcept = toUnsavedConceptEntry(projectKey, taskKey, username, conceptIdNode.asText());
				if (unsavedConcept != null) {
					unsavedConcepts.add(unsavedConcept);
				}
			}
		} catch (IOException e) {
			logger.error("Failed to read modified concepts for task {}/{}: {}", projectKey, taskKey, e.getMessage());
		}
		return unsavedConcepts;
	}

	private UnsavedConcept toUnsavedConceptEntry(String projectKey, String taskKey, String username, String conceptId)
			throws IOException {
		if (!isSctid(conceptId)) {
			return null;
		}
		JsonNode concept = uiStateService.retrieveTaskPanelStateWithoutThrowingResourceNotFoundException(
				projectKey, taskKey, username, CONCEPT_PANEL_PREFIX + conceptId);
		if (concept == null || isCurrentPlaceholder(concept)) {
			return null;
		}
		String displayConceptId = concept.path("conceptId").asText(null);
		if (!StringUtils.hasLength(displayConceptId)) {
			displayConceptId = "(New concept)";
		}
		return new UnsavedConcept(displayConceptId, extractFsn(concept));
	}

	private void evaluateClassification(Branch branch, Classification classification,
			TraceabilityClient.ActivitiesPage activities, ReviewPrerequisites prerequisites, List<String> blockers) {
		// Mirrors authoring-ui reviewService.checkClassificationPrerequisites
		if (branch == null) {
			prerequisites.setClassificationCurrent(false);
			prerequisites.setClassificationStatus(null);
			blockers.add("Branch Not Provided: Branch not provided to submit for review. This is a fatal error: contact an administrator");
			return;
		}

		if (classification == null) {
			prerequisites.setClassificationCurrent(false);
			prerequisites.setClassificationStatus(null);
			blockers.add("Classification Not Run: No classifications were run on this branch.");
			return;
		}

		ClassificationStatus status = classification.getStatus();
		prerequisites.setClassificationStatus(status != null ? status.name() : null);

		boolean statusOk = isClassificationStatusOk(status);
		if (!statusOk) {
			blockers.add("Classification Not Completed: Classification was started for this branch, but either failed or has not completed.");
		}

		boolean current = checkClassificationCurrency(status, classification, branch, activities, blockers);
		checkClassificationAcceptance(status, classification, blockers);
		prerequisites.setClassificationCurrent(statusOk && current);
	}

	private static boolean isClassificationStatusOk(ClassificationStatus status) {
		return status == ClassificationStatus.COMPLETED
				|| status == ClassificationStatus.SAVING_IN_PROGRESS
				|| status == ClassificationStatus.SAVED;
	}

	private boolean checkClassificationCurrency(ClassificationStatus status, Classification classification,
			Branch branch, TraceabilityClient.ActivitiesPage activities, List<String> blockers) {
		if (status == ClassificationStatus.COMPLETED) {
			return isCompletedClassificationCurrent(classification, branch, blockers);
		}
		if (status == ClassificationStatus.SAVED) {
			return isSavedClassificationCurrent(classification, activities, blockers);
		}
		return true;
	}

	private static boolean isCompletedClassificationCurrent(Classification classification, Branch branch,
			List<String> blockers) {
		Date creationDate = classification.getCreationDate();
		if (creationDate != null && creationDate.getTime() < branch.getHeadTimestamp()) {
			blockers.add("Classification Not Current: Classification was run, but modifications were made after the classifier was initiated.");
			return false;
		}
		return true;
	}

	private boolean isSavedClassificationCurrent(Classification classification,
			TraceabilityClient.ActivitiesPage activities, List<String> blockers) {
		if (classification.getSaveDate() == null) {
			blockers.add("Classification May Not Be Current: Could not determine whether modifications were made after saving the classification.");
			return false;
		}
		if (!isClassificationSavedCurrent(activities)) {
			blockers.add("Classification Not Current: Classification was run, but modifications were made to the task afterwards.");
			return false;
		}
		return true;
	}

	private static void checkClassificationAcceptance(ClassificationStatus status, Classification classification,
			List<String> blockers) {
		boolean hasResults = Boolean.TRUE.equals(classification.getEquivalentConceptsFound())
				|| Boolean.TRUE.equals(classification.getInferredRelationshipChangesFound())
				|| Boolean.TRUE.equals(classification.getRedundantStatedRelationshipsFound());
		if (status != ClassificationStatus.SAVED && hasResults) {
			blockers.add("Classification Not Accepted: Classification results were not accepted to this branch");
		}
		if (Boolean.TRUE.equals(classification.getEquivalentConceptsFound())) {
			blockers.add("Equivalencies Found: Classification reports equivalent concepts on this branch. You may not submit for review until these are resolved");
		}
	}

	private boolean isClassificationSavedCurrent(TraceabilityClient.ActivitiesPage activities) {
		List<TraceabilityClient.Activity> content = activities.getContent();
		if (content == null || content.isEmpty()) {
			return false;
		}
		TraceabilityClient.Activity lastActivity = content.get(content.size() - 1);
		if (lastActivity.getCommitDate() == null) {
			return false;
		}
		long lastModifiedTime = lastActivity.getCommitDate().getTime();
		long lastClassificationSaved = 0;
		for (TraceabilityClient.Activity activity : content) {
			if (CLASSIFICATION_SAVE.equals(activity.getActivityType()) && activity.getCommitDate() != null) {
				lastClassificationSaved = activity.getCommitDate().getTime();
			}
		}
		return lastClassificationSaved == lastModifiedTime;
	}

	private static boolean isCurrentPlaceholder(JsonNode concept) {
		return concept.has("current") && concept.get("current").asBoolean(false);
	}

	private static String extractFsn(JsonNode concept) {
		if (concept.hasNonNull("fsn") && concept.get("fsn").isTextual()) {
			return concept.get("fsn").asText();
		}
		JsonNode descriptions = concept.get("descriptions");
		if (descriptions != null && descriptions.isArray()) {
			for (JsonNode description : descriptions) {
				if ("FSN".equals(description.path("type").asText())) {
					String term = description.path("term").asText(null);
					if (StringUtils.hasLength(term)) {
						return term;
					}
				}
			}
		}
		return COULD_NOT_DETERMINE_FSN;
	}

	private static boolean isSctid(String id) {
		return CrsBlockingStateService.isSctid(id);
	}
}
