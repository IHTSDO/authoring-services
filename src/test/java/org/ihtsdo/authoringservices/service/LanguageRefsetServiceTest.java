package org.ihtsdo.authoringservices.service;

import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ConceptMiniPojo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.ihtsdo.authoringservices.service.LanguageRefsetService.LANGUAGE_REFSET_TYPE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LanguageRefsetServiceTest {

	private static final String BRANCH = "MAIN/SNOMEDCT-XX/PROJECT/TASK-1";
	private static final String US_EN_ID = "900000000000509007";
	private static final String GB_EN_ID = "900000000000508004";

	@Mock
	private SnowstormRestClientFactory snowstormRestClientFactory;
	@Mock
	private SnowstormRestClient snowstormRestClient;

	private LanguageRefsetService service;

	@BeforeEach
	void setUp() {
		service = new LanguageRefsetService(snowstormRestClientFactory);
		when(snowstormRestClientFactory.getClient()).thenReturn(snowstormRestClient);
	}

	@Test
	void getLanguageRefsets_returnsConceptMinisFromTerminologyServer() {
		ConceptMiniPojo usEnglish = concept(US_EN_ID);
		ConceptMiniPojo gbEnglish = concept(GB_EN_ID);
		Map<String, ConceptMiniPojo> refsets = refsetMap(usEnglish, gbEnglish);
		when(snowstormRestClient.getRefsetsWithTypeInformation(eq(BRANCH), eq(true), isNull(),
				eq(LANGUAGE_REFSET_TYPE_ID), isNull()))
				.thenReturn(refsets);

		List<ConceptMiniPojo> result = service.getLanguageRefsets(BRANCH, null);

		assertEquals(List.of(usEnglish, gbEnglish), result);
	}

	@Test
	void getLanguageRefsets_forwardsAcceptLanguageToTerminologyServer() {
		String acceptLanguage = "en-US;q=0.8,en-GB;q=0.5";
		ConceptMiniPojo usEnglish = concept(US_EN_ID);
		Map<String, ConceptMiniPojo> refsets = refsetMap(usEnglish);
		when(snowstormRestClient.getRefsetsWithTypeInformation(eq(BRANCH), eq(true), isNull(),
				eq(LANGUAGE_REFSET_TYPE_ID), eq(acceptLanguage)))
				.thenReturn(refsets);

		List<ConceptMiniPojo> result = service.getLanguageRefsets(BRANCH, acceptLanguage);

		assertEquals(List.of(usEnglish), result);
		verify(snowstormRestClient).getRefsetsWithTypeInformation(BRANCH, true, null, LANGUAGE_REFSET_TYPE_ID, acceptLanguage);
	}

	@Test
	void getLanguageRefsets_returnsEmptyWhenBranchHasNoLanguageRefsets() {
		when(snowstormRestClient.getRefsetsWithTypeInformation(eq(BRANCH), eq(true), isNull(),
				eq(LANGUAGE_REFSET_TYPE_ID), isNull()))
				.thenReturn(Map.of());

		List<ConceptMiniPojo> result = service.getLanguageRefsets(BRANCH, null);

		assertTrue(result.isEmpty());
	}

	private static Map<String, ConceptMiniPojo> refsetMap(ConceptMiniPojo... concepts) {
		Map<String, ConceptMiniPojo> refsets = new LinkedHashMap<>();
		for (ConceptMiniPojo concept : concepts) {
			refsets.put(concept.getConceptId(), concept);
		}
		return refsets;
	}

	private static ConceptMiniPojo concept(String conceptId) {
		ConceptMiniPojo concept = mock(ConceptMiniPojo.class);
		when(concept.getConceptId()).thenReturn(conceptId);
		return concept;
	}
}
