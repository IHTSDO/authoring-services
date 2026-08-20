package org.ihtsdo.authoringservices.rest;

import org.ihtsdo.authoringservices.rest.LanguageRefsetController.LanguageRefset;
import org.ihtsdo.authoringservices.service.LanguageRefsetService;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ConceptMiniPojo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
class LanguageRefsetControllerTest {

	private static final String BRANCH = "MAIN/SNOMEDCT-XX/PROJECT/TASK-1";
	private static final String US_EN_ID = "900000000000509007";
	private static final String GB_EN_ID = "900000000000508004";

	@Mock
	private LanguageRefsetService languageRefsetService;

	private LanguageRefsetController controller;

	@BeforeEach
	void setUp() {
		controller = new LanguageRefsetController(languageRefsetService);
	}

	@Test
	void getLanguageRefsets_mapsConceptMinisToIdTermAndActive() {
		ConceptMiniPojo usEnglish = concept(US_EN_ID, "United States of America English language reference set", "US English", true);
		ConceptMiniPojo gbEnglish = concept(GB_EN_ID, "Great Britain English language reference set", "GB English", false);
		when(languageRefsetService.getLanguageRefsets(BRANCH, null)).thenReturn(List.of(usEnglish, gbEnglish));

		List<LanguageRefset> result = controller.getLanguageRefsets(BRANCH, null);

		assertEquals(List.of(
				new LanguageRefset(US_EN_ID, true, "United States of America English language reference set", "US English"),
				new LanguageRefset(GB_EN_ID, false, "Great Britain English language reference set", "GB English")), result);
	}

	@Test
	void getLanguageRefsets_returnsEmptyWhenServiceReturnsNone() {
		when(languageRefsetService.getLanguageRefsets(BRANCH, null)).thenReturn(List.of());

		List<LanguageRefset> result = controller.getLanguageRefsets(BRANCH, null);

		assertTrue(result.isEmpty());
	}

	private static ConceptMiniPojo concept(String conceptId, String fsn, String preferredTerm, boolean active) {
		ConceptMiniPojo concept = mock(ConceptMiniPojo.class,
				withSettings().lenient().defaultAnswer(Answers.RETURNS_DEEP_STUBS));
		when(concept.getConceptId()).thenReturn(conceptId);
		when(concept.getFsn().getTerm()).thenReturn(fsn);
		when(concept.getPt().getTerm()).thenReturn(preferredTerm);
		when(concept.getActive()).thenReturn(active);
		return concept;
	}
}
