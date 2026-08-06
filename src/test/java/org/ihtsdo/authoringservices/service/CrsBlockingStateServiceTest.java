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

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrsBlockingStateServiceTest {

	private static final String PROJECT = "PROJ";
	private static final String TASK = "PROJ-1";
	private static final String USER = "author";
	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Mock
	private UiStateService uiStateService;

	private CrsBlockingStateService service;

	@BeforeEach
	void setUp() {
		service = new CrsBlockingStateService(uiStateService);
	}

	@Test
	void getBlockingState_returnsBlockedWhenUnsavedNewConceptHasSctid() throws IOException {
		ArrayNode concepts = MAPPER.createArrayNode();
		concepts.add(crsConcept("12345678901", "99", false, true, "Pneumonia (disorder)", null));
		when(uiStateService.retrieveTaskPanelStateWithoutThrowingResourceNotFoundException(
				eq(PROJECT), eq(TASK), eq("SHARED"), eq("crs-concepts"))).thenReturn(concepts);

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
	void getBlockingState_ignoresSavedConceptsAndNonSctids() throws IOException {
		ArrayNode concepts = MAPPER.createArrayNode();
		concepts.add(crsConcept("12345678901", "1", true, true, "Saved", null));
		concepts.add(crsConcept("12345678902", "2", false, false, "Not new", null));
		concepts.add(crsConcept("uuid-not-sctid", "3", false, true, "Temp id", null));
		when(uiStateService.retrieveTaskPanelStateWithoutThrowingResourceNotFoundException(
				eq(PROJECT), eq(TASK), eq("SHARED"), eq("crs-concepts"))).thenReturn(concepts);

		CrsBlockingState state = service.getBlockingState(PROJECT, TASK, USER);

		assertFalse(state.isBlocked());
		assertTrue(state.getBlockingConcepts().isEmpty());
	}

	@Test
	void getBlockingState_fallsBackToUserPanelWhenSharedMissing() throws IOException {
		ArrayNode concepts = MAPPER.createArrayNode();
		concepts.add(crsConcept("999", "7", false, true, null, "Preferred term"));
		when(uiStateService.retrieveTaskPanelStateWithoutThrowingResourceNotFoundException(
				eq(PROJECT), eq(TASK), eq("SHARED"), eq("crs-concepts"))).thenReturn(null);
		when(uiStateService.retrieveTaskPanelStateWithoutThrowingResourceNotFoundException(
				eq(PROJECT), eq(TASK), eq(USER), eq("crs-concepts"))).thenReturn(concepts);

		CrsBlockingState state = service.getBlockingState(PROJECT, TASK, USER);

		assertTrue(state.isBlocked());
		assertEquals(List.of(new BlockingConcept("999", "7", null, "Preferred term")), state.getBlockingConcepts());
	}

	@Test
	void getBlockingState_usesStatusFromUiStateWhenPresent() throws IOException {
		ArrayNode concepts = MAPPER.createArrayNode();
		concepts.add(crsConcept("111", "5", false, true, "FSN", "ACCEPTED"));
		when(uiStateService.retrieveTaskPanelStateWithoutThrowingResourceNotFoundException(
				eq(PROJECT), eq(TASK), eq("SHARED"), eq("crs-concepts"))).thenReturn(concepts);

		BlockingConcept concept = service.getBlockingState(PROJECT, TASK, USER).getBlockingConcepts().get(0);

		assertEquals("ACCEPTED", concept.status());
	}

	@Test
	void isSctid_requiresNonEmptyDigitsOnly() {
		assertTrue(CrsBlockingStateService.isSctid("123"));
		assertFalse(CrsBlockingStateService.isSctid(""));
		assertFalse(CrsBlockingStateService.isSctid(null));
		assertFalse(CrsBlockingStateService.isSctid("12a3"));
	}

	private static JsonNode crsConcept(String conceptId, String crsId, boolean saved, boolean isNewConcept,
			String fsn, String statusOrPreferred) {
		ObjectNode node = MAPPER.createObjectNode();
		node.put("conceptId", conceptId);
		node.put("crsId", crsId);
		node.put("saved", saved);
		node.put("isNewConcept", isNewConcept);
		if (fsn != null) {
			node.put("fsn", fsn);
		}
		if (statusOrPreferred != null && fsn == null) {
			node.put("preferredSynonym", statusOrPreferred);
		} else if (statusOrPreferred != null) {
			node.put("status", statusOrPreferred);
		}
		return node;
	}
}
