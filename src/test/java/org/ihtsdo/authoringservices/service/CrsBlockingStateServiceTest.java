package org.ihtsdo.authoringservices.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ihtsdo.authoringservices.domain.CrsBlockingState;
import org.ihtsdo.authoringservices.domain.CrsBlockingState.BlockingConcept;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrsBlockingStateServiceTest {

	private static final String PROJECT = "PROJ";
	private static final String TASK = "PROJ-1";
	private static final String USER = "author";
	private static final String SHARED = "SHARED";
	private static final String CRS_CONCEPTS_PANEL = "crs-concepts";
	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Mock
	private UiStateService uiStateService;

	private CrsBlockingStateService service;

	@BeforeEach
	void setUp() {
		service = new CrsBlockingStateService(uiStateService);
	}

	@Test
	void getBlockingState_returnsBlockedWhenUnsavedNewConceptHasSctid() throws Exception {
		stubSharedCrsConcepts(arrayOf(crsConcept("12345678901", "99", false, true, "Pneumonia (disorder)")));

		CrsBlockingState state = service.getBlockingState(PROJECT, TASK, USER);

		assertTrue(state.isBlocked());
		assertEquals(1, state.getBlockingConcepts().size());
		BlockingConcept concept = state.getBlockingConcepts().get(0);
		assertEquals("12345678901", concept.conceptId());
		assertEquals("99", concept.crsRequestId());
		assertNull(concept.status());
		assertEquals("Pneumonia (disorder)", concept.requestSummary());
	}

	@Test
	void getBlockingState_ignoresSavedConceptsAndNonSctids() throws Exception {
		stubSharedCrsConcepts(arrayOf(
				crsConcept("12345678901", "1", true, true, "Saved"),
				crsConcept("12345678902", "2", false, false, "Not new"),
				crsConcept("uuid-not-sctid", "3", false, true, "Temp id")));

		CrsBlockingState state = service.getBlockingState(PROJECT, TASK, USER);

		assertFalse(state.isBlocked());
		assertEquals(0, state.getBlockingConcepts().size());
	}

	@Test
	void getBlockingState_fallsBackToUserPanelWhenSharedMissing() throws Exception {
		stubSharedCrsConcepts(null);
		ObjectNode conceptNode = crsConcept("999", "7", false, true);
		conceptNode.put("preferredSynonym", "Preferred term");
		stubUserCrsConcepts(arrayOf(conceptNode));

		CrsBlockingState state = service.getBlockingState(PROJECT, TASK, USER);

		assertTrue(state.isBlocked());
		assertEquals(1, state.getBlockingConcepts().size());
		BlockingConcept concept = state.getBlockingConcepts().get(0);
		assertEquals("999", concept.conceptId());
		assertEquals("7", concept.crsRequestId());
		assertNull(concept.status());
		assertEquals("Preferred term", concept.requestSummary());
	}

	@Test
	void getBlockingState_usesStatusFromUiStateWhenPresent() throws Exception {
		ObjectNode concept = crsConcept("111", "5", false, true, "FSN");
		concept.put("status", "ACCEPTED");
		stubSharedCrsConcepts(arrayOf(concept));

		BlockingConcept blockingConcept = service.getBlockingState(PROJECT, TASK, USER).getBlockingConcepts().get(0);

		assertEquals("ACCEPTED", blockingConcept.status());
	}

	@Test
	void isSctid_requiresNonEmptyDigitsOnly() {
		assertTrue(CrsBlockingStateService.isSctid("123"));
		assertFalse(CrsBlockingStateService.isSctid(""));
		assertFalse(CrsBlockingStateService.isSctid(null));
		assertFalse(CrsBlockingStateService.isSctid("12a3"));
	}

	private void stubSharedCrsConcepts(JsonNode concepts) throws Exception {
		when(uiStateService.retrieveTaskPanelStateWithoutThrowingResourceNotFoundException(
				PROJECT, TASK, SHARED, CRS_CONCEPTS_PANEL)).thenReturn(concepts);
	}

	private void stubUserCrsConcepts(JsonNode concepts) throws Exception {
		when(uiStateService.retrieveTaskPanelStateWithoutThrowingResourceNotFoundException(
				PROJECT, TASK, USER, CRS_CONCEPTS_PANEL)).thenReturn(concepts);
	}

	private static ArrayNode arrayOf(JsonNode... concepts) {
		ArrayNode array = MAPPER.createArrayNode();
		for (JsonNode concept : concepts) {
			array.add(concept);
		}
		return array;
	}

	private static ObjectNode crsConcept(String conceptId, String crsId, boolean saved, boolean isNewConcept) {
		return crsConcept(conceptId, crsId, saved, isNewConcept, null);
	}

	private static ObjectNode crsConcept(String conceptId, String crsId, boolean saved, boolean isNewConcept, String fsn) {
		ObjectNode node = MAPPER.createObjectNode();
		node.put("conceptId", conceptId);
		node.put("crsId", crsId);
		node.put("saved", saved);
		node.put("isNewConcept", isNewConcept);
		if (fsn != null) {
			node.put("fsn", fsn);
		}
		return node;
	}
}
