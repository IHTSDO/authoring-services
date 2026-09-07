package org.ihtsdo.authoringservices.service;

import tools.jackson.databind.JsonNode;
import org.ihtsdo.authoringservices.domain.ReviewPrerequisites;
import org.ihtsdo.authoringservices.domain.ReviewPrerequisites.UnsavedConcept;
import org.ihtsdo.authoringservices.service.ClassificationPrerequisiteService.ClassificationPrerequisiteResult;
import org.ihtsdo.authoringservices.service.ClassificationPrerequisiteService.Context;
import org.ihtsdo.authoringservices.service.client.TraceabilityClient;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Branch;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Classification;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReviewPrerequisitesService {

	private static final String MODIFIED_LIST_PANEL = "modified-list";
	private static final String CONCEPT_PANEL_PREFIX = "concept-";
	private static final String COULD_NOT_DETERMINE_FSN = "Could not determine FSN";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final UiStateService uiStateService;
	private final BranchService branchService;
	private final ClassificationPrerequisiteService classificationPrerequisiteService;
	private final ContentRequestService contentRequestService;

	public ReviewPrerequisitesService(UiStateService uiStateService, BranchService branchService,
			ClassificationPrerequisiteService classificationPrerequisiteService,
			ContentRequestService contentRequestService) {
		this.uiStateService = uiStateService;
		this.branchService = branchService;
		this.classificationPrerequisiteService = classificationPrerequisiteService;
		this.contentRequestService = contentRequestService;
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

		TraceabilityClient.ActivitiesPage activities = classificationPrerequisiteService.fetchActivities(branchPath);
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

		Classification classification = classificationPrerequisiteService.getLatestClassificationOrNull(branchPath);
		ClassificationPrerequisiteResult classificationResult = classificationPrerequisiteService.evaluate(
				branch, classification, activities, Context.REVIEW);
		prerequisites.setClassificationCurrent(classificationResult.classificationCurrent());
		prerequisites.setClassificationStatus(classificationResult.classificationStatus());
		blockers.addAll(classificationResult.blockers());

		List<String> crsBlockingConcepts = contentRequestService.formatBlockingConcepts(
				contentRequestService.collectBlockingConcepts(projectKey, taskKey, username));
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

	private List<UnsavedConcept> collectUnsavedConcepts(String projectKey, String taskKey, String username) {
		List<UnsavedConcept> unsavedConcepts = new ArrayList<>();
		try {
			JsonNode modifiedList = uiStateService.retrieveTaskPanelStateWithoutThrowingResourceNotFoundException(
					projectKey, taskKey, username, MODIFIED_LIST_PANEL);
			if (modifiedList == null || !modifiedList.isArray()) {
				return unsavedConcepts;
			}
			for (JsonNode conceptIdNode : modifiedList) {
				UnsavedConcept unsavedConcept = toUnsavedConceptEntry(projectKey, taskKey, username, conceptIdNode.asString());
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
		String displayConceptId = concept.path("conceptId").asString(null);
		if (!StringUtils.hasLength(displayConceptId)) {
			displayConceptId = "(New concept)";
		}
		return new UnsavedConcept(displayConceptId, extractFsn(concept));
	}

	private static boolean isCurrentPlaceholder(JsonNode concept) {
		return concept.has("current") && concept.get("current").asBoolean(false);
	}

	private static String extractFsn(JsonNode concept) {
		if (concept.hasNonNull("fsn") && concept.get("fsn").isString()) {
			return concept.get("fsn").asString();
		}
		JsonNode descriptions = concept.get("descriptions");
		if (descriptions != null && descriptions.isArray()) {
			for (JsonNode description : descriptions) {
				if ("FSN".equals(description.path("type").asString())) {
					String term = description.path("term").asString(null);
					if (StringUtils.hasLength(term)) {
						return term;
					}
				}
			}
		}
		return COULD_NOT_DETERMINE_FSN;
	}

	private static boolean isSctid(String id) {
		return ContentRequestService.isSctid(id);
	}
}
