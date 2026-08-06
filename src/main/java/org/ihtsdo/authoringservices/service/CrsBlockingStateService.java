package org.ihtsdo.authoringservices.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.ihtsdo.authoringservices.domain.CrsBlockingState;
import org.ihtsdo.authoringservices.domain.CrsBlockingState.BlockingConcept;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Determines whether any CRS concepts on a task block promotion or review submission.
 * Mirrors authoring-ui: !saved && isNewConcept && isSctid(conceptId).
 */
@Service
public class CrsBlockingStateService {

	private static final String SHARED = "SHARED";
	private static final String CRS_CONCEPTS_PANEL = "crs-concepts";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final UiStateService uiStateService;

	public CrsBlockingStateService(UiStateService uiStateService) {
		this.uiStateService = uiStateService;
	}

	public CrsBlockingState getBlockingState(String projectKey, String taskKey, String username) {
		CrsBlockingState state = new CrsBlockingState();
		state.setBlockingConcepts(collectBlockingConcepts(projectKey, taskKey, username));
		return state;
	}

	public List<BlockingConcept> collectBlockingConcepts(String projectKey, String taskKey, String username) {
		List<BlockingConcept> blocking = new ArrayList<>();
		try {
			JsonNode crsConcepts = uiStateService.retrieveTaskPanelStateWithoutThrowingResourceNotFoundException(
					projectKey, taskKey, SHARED, CRS_CONCEPTS_PANEL);
			if (crsConcepts == null) {
				crsConcepts = uiStateService.retrieveTaskPanelStateWithoutThrowingResourceNotFoundException(
						projectKey, taskKey, username, CRS_CONCEPTS_PANEL);
			}
			if (crsConcepts == null || !crsConcepts.isArray()) {
				return blocking;
			}
			for (JsonNode crsConcept : crsConcepts) {
				BlockingConcept blockingConcept = toBlockingConceptIfApplicable(crsConcept);
				if (blockingConcept != null) {
					blocking.add(blockingConcept);
				}
			}
		} catch (IOException e) {
			logger.error("Failed to read CRS concepts for task {}/{}: {}", projectKey, taskKey, e.getMessage());
		}
		return blocking;
	}

	private static BlockingConcept toBlockingConceptIfApplicable(JsonNode crsConcept) {
		boolean saved = crsConcept.path("saved").asBoolean(false);
		boolean isNewConcept = crsConcept.path("isNewConcept").asBoolean(false);
		String conceptId = textOrNull(crsConcept, "conceptId");
		if (saved || !isNewConcept || !isSctid(conceptId)) {
			return null;
		}
		String crsRequestId = textOrEmpty(crsConcept, "crsId");
		String status = textOrNull(crsConcept, "status");
		String requestSummary = firstNonBlank(
				textOrNull(crsConcept, "fsn"),
				textOrNull(crsConcept, "preferredSynonym"));
		return new BlockingConcept(conceptId, crsRequestId, status, requestSummary);
	}

	private static String firstNonBlank(String first, String second) {
		if (StringUtils.hasLength(first)) {
			return first;
		}
		return StringUtils.hasLength(second) ? second : null;
	}

	private static String textOrNull(JsonNode node, String field) {
		String value = node.path(field).asText(null);
		return StringUtils.hasLength(value) ? value : null;
	}

	private static String textOrEmpty(JsonNode node, String field) {
		String value = textOrNull(node, field);
		return value != null ? value : "";
	}

	static boolean isSctid(String id) {
		if (!StringUtils.hasLength(id)) {
			return false;
		}
		for (int i = 0; i < id.length(); i++) {
			if (!Character.isDigit(id.charAt(i))) {
				return false;
			}
		}
		return true;
	}
}
