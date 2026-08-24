package org.ihtsdo.authoringservices.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.sf.json.JSONObject;
import org.ihtsdo.authoringservices.domain.CrsBlockingState;
import org.ihtsdo.authoringservices.domain.CrsBlockingState.BlockingConcept;
import org.ihtsdo.authoringservices.domain.UiConfiguration;
import org.ihtsdo.authoringservices.service.client.ContentRequestServiceClient;
import org.ihtsdo.authoringservices.service.client.ContentRequestServiceClient.ContentRequestDto;
import org.ihtsdo.authoringservices.service.client.ContentRequestServiceClientFactory;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.AxiomPojo;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ConceptPojo;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.DescriptionPojo;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RelationshipPojo;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentRequestServiceTest {

	private static final String PROJECT = "WRPAS";
	private static final String TASK = "WRPAS-1";
	private static final String REQUEST_ID = "99";
	private static final String INT_BRANCH = "MAIN/WRPAS/WRPAS-1";
	private static final String US_BRANCH = "MAIN/SNOMEDCT-US/USPROJ/USPROJ-1";
	private static final String INT_CRS = "https://crs.example/int/";
	private static final String US_CRS = "https://crs.example/us/";
	private static final String MODULE_ID = "900000000000207008";
	private static final String CONCEPT_ID = "12345678901";
	private static final String ISA = "116680003";
	private static final String USER = "author";
	private static final String SHARED = "SHARED";
	private static final String CRS_CONCEPTS_PANEL = "crs-concepts";

	@Mock
	private PermissionService permissionService;
	@Mock
	private BranchService branchService;
	@Mock
	private SnowstormRestClientFactory snowstormRestClientFactory;
	@Mock
	private ContentRequestServiceClientFactory contentRequestServiceClientFactory;
	@Mock
	private UiConfiguration uiConfiguration;
	@Mock
	private UiStateService uiStateService;
	@Mock
	private SnowstormRestClient snowstormRestClient;
	@Mock
	private ContentRequestServiceClient crsClient;
	@Mock
	private ContentRequestDto contentRequestDto;

	private static final ObjectMapper objectMapper = new ObjectMapper();
	private ContentRequestService service;

	@BeforeEach
	void setUp() {
		CrsConceptPreparation preparation = new CrsConceptPreparation(objectMapper, () -> "fixed-uuid");
		service = new ContentRequestService(permissionService, branchService, snowstormRestClientFactory,
				contentRequestServiceClientFactory, uiConfiguration, preparation, uiStateService, objectMapper);
	}

	@Test
	void applyRequest_routesInternationalBranchToInternationalCrs() throws Exception {
		stubBranch(INT_BRANCH);
		stubCrsEndpoints();
		when(contentRequestServiceClientFactory.getClient(INT_CRS)).thenReturn(crsClient);
		when(crsClient.getRequestDetails(REQUEST_ID)).thenReturn(contentRequestDto);
		when(contentRequestDto.getConcept()).thenReturn(null);
		when(branchService.getBranchMetadataIncludeInherited(INT_BRANCH)).thenReturn(Map.of("defaultModuleId", MODULE_ID));

		ObjectNode result = service.applyRequest(PROJECT, TASK, REQUEST_ID);

		assertEquals("Request without proposed concept", result.path("fsn").asText());
		verify(contentRequestServiceClientFactory).getClient(INT_CRS);
		verify(snowstormRestClientFactory, never()).getClient();
	}

	@Test
	void applyRequest_routesUsExtensionBranchToUsCrs() throws Exception {
		stubBranch(US_BRANCH);
		stubCrsEndpoints();
		when(contentRequestServiceClientFactory.getClient(US_CRS)).thenReturn(crsClient);
		when(crsClient.getRequestDetails(REQUEST_ID)).thenReturn(contentRequestDto);
		when(contentRequestDto.getConcept()).thenReturn(crsConcept("""
				{
				  "fsn": "New (disorder)",
				  "definitionOfChanges": { "changeType": "NEW_CONCEPT" },
				  "relationships": [
				    {
				      "characteristicType": "STATED_RELATIONSHIP",
				      "groupId": 0,
				      "active": true,
				      "type": { "conceptId": "%s", "fsn": "Is a (attribute)" },
				      "target": { "conceptId": "404684003" }
				    }
				  ]
				}
				""".formatted(ISA)));
		when(branchService.getBranchMetadataIncludeInherited(US_BRANCH)).thenReturn(Map.of("defaultModuleId", MODULE_ID));

		ObjectNode result = service.applyRequest(PROJECT, TASK, REQUEST_ID);

		assertEquals("fixed-uuid", result.path("conceptId").asText());
		assertEquals("Is a", result.path("classAxioms").get(0).path("relationships").get(0).path("type").path("pt").asText());
		verify(contentRequestServiceClientFactory).getClient(US_CRS);
		verify(snowstormRestClientFactory, never()).getClient();
	}

	@Test
	void applyRequest_mergesCrsRequestIntoConceptOnTaskBranch() throws Exception {
		stubBranch(INT_BRANCH);
		stubCrsEndpoints();
		when(contentRequestServiceClientFactory.getClient(INT_CRS)).thenReturn(crsClient);
		when(crsClient.getRequestDetails(REQUEST_ID)).thenReturn(contentRequestDto);
		when(contentRequestDto.getConcept()).thenReturn(crsConcept("""
				{
				  "conceptId": "%s",
				  "definitionStatus": "FULLY_DEFINED",
				  "definitionOfChanges": { "notes": "CRS notes" },
				  "descriptions": [
				    { "descriptionId": "d1", "definitionOfChanges": { "changed": true } }
				  ],
				  "relationships": []
				}
				""".formatted(CONCEPT_ID)));
		when(branchService.getBranchMetadataIncludeInherited(INT_BRANCH)).thenReturn(Map.of("defaultModuleId", MODULE_ID));
		when(snowstormRestClientFactory.getClient()).thenReturn(snowstormRestClient);
		when(snowstormRestClient.getConcept(INT_BRANCH, CONCEPT_ID)).thenReturn(existingConcept());

		ObjectNode result = service.applyRequest(PROJECT, TASK, REQUEST_ID);

		assertEquals(CONCEPT_ID, result.path("conceptId").asText());
		assertEquals("CRS notes", result.path("definitionOfChanges").path("notes").asText());
		assertTrue(result.path("descriptions").get(0).path("definitionOfChanges").path("changed").asBoolean());
		assertEquals("FULLY_DEFINED", result.path("definitionStatus").asText());
	}

	@Test
	void applyRequest_throwsWhenCrsRequestMissing() throws Exception {
		stubBranch(INT_BRANCH);
		stubCrsEndpoints();
		when(contentRequestServiceClientFactory.getClient(INT_CRS)).thenReturn(crsClient);
		when(crsClient.getRequestDetails(REQUEST_ID)).thenThrow(
				HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
						new HttpHeaders(), new byte[0], StandardCharsets.UTF_8));

		assertThrows(ResourceNotFoundException.class, () -> service.applyRequest(PROJECT, TASK, REQUEST_ID));
	}

	@Test
	void applyRequest_throwsWhenConceptMissingOnBranch() throws Exception {
		stubBranch(INT_BRANCH);
		stubCrsEndpoints();
		when(contentRequestServiceClientFactory.getClient(INT_CRS)).thenReturn(crsClient);
		when(crsClient.getRequestDetails(REQUEST_ID)).thenReturn(contentRequestDto);
		when(contentRequestDto.getConcept()).thenReturn(crsConcept("{\"conceptId\":\"" + CONCEPT_ID + "\"}"));
		when(snowstormRestClientFactory.getClient()).thenReturn(snowstormRestClient);
		when(snowstormRestClient.getConcept(INT_BRANCH, CONCEPT_ID)).thenThrow(new RestClientException("not found"));

		BusinessServiceException ex = assertThrows(BusinessServiceException.class,
				() -> service.applyRequest(PROJECT, TASK, REQUEST_ID));

		assertTrue(ex.getMessage().contains(CONCEPT_ID));
	}

	@Test
	void applyRequest_requiresWritePermission() {
		doThrow(new AccessDeniedException("no permission")).when(permissionService)
				.checkFullPermissionOnProjectOrThrow(PROJECT);

		assertThrows(AccessDeniedException.class, () -> service.applyRequest(PROJECT, TASK, REQUEST_ID));
	}

	@Test
	void applyRequest_requiresRequestId() {
		assertThrows(IllegalArgumentException.class, () -> service.applyRequest(PROJECT, TASK, " "));
	}

	@Test
	void isInternationalBranch_treatsExtensionPrefixAsUsOrManagedService() {
		assertTrue(ContentRequestService.isInternationalBranch(INT_BRANCH));
		assertTrue(ContentRequestService.isInternationalBranch("MAIN"));
		assertFalse(ContentRequestService.isInternationalBranch(US_BRANCH));
		assertFalse(ContentRequestService.isInternationalBranch("MAIN/SNOMEDCT-AU/P/T"));
	}

	@Test
	void getBlockingState_returnsBlockedWhenUnsavedNewConceptHasSctid() throws Exception {
		stubSharedCrsConcepts(arrayOf(blockingCrsConcept("12345678901", "99", false, true, "Pneumonia (disorder)")));

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
				blockingCrsConcept("12345678901", "1", true, true, "Saved"),
				blockingCrsConcept("12345678902", "2", false, false, "Not new"),
				blockingCrsConcept("uuid-not-sctid", "3", false, true, "Temp id")));

		CrsBlockingState state = service.getBlockingState(PROJECT, TASK, USER);

		assertFalse(state.isBlocked());
		assertEquals(0, state.getBlockingConcepts().size());
	}

	@Test
	void getBlockingState_fallsBackToUserPanelWhenSharedMissing() throws Exception {
		stubSharedCrsConcepts(null);
		ObjectNode conceptNode = blockingCrsConcept("999", "7", false, true, null);
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
		ObjectNode concept = blockingCrsConcept("111", "5", false, true, "FSN");
		concept.put("status", "ACCEPTED");
		stubSharedCrsConcepts(arrayOf(concept));

		BlockingConcept blockingConcept = service.getBlockingState(PROJECT, TASK, USER).getBlockingConcepts().get(0);

		assertEquals("ACCEPTED", blockingConcept.status());
	}

	@Test
	void isSctid_requiresNonEmptyDigitsOnly() {
		assertTrue(ContentRequestService.isSctid("123"));
		assertFalse(ContentRequestService.isSctid(""));
		assertFalse(ContentRequestService.isSctid(null));
		assertFalse(ContentRequestService.isSctid("12a3"));
	}

	private void stubBranch(String branchPath) throws Exception {
		when(branchService.getTaskBranchPathUsingCache(PROJECT, TASK)).thenReturn(branchPath);
	}

	private void stubCrsEndpoints() {
		when(uiConfiguration.getEndpoints()).thenReturn(Map.of(
				ContentRequestService.CRS_ENDPOINT, INT_CRS,
				ContentRequestService.CRS_ENDPOINT_US, US_CRS));
	}

	private static JSONObject crsConcept(String json) {
		return JSONObject.fromObject(json);
	}

	private static ConceptPojo existingConcept() {
		ConceptPojo concept = new ConceptPojo(CONCEPT_ID);
		concept.setActive(true);
		DescriptionPojo description = new DescriptionPojo();
		description.setDescriptionId("d1");
		description.setTerm("Existing");
		concept.setDescriptions(Set.of(description));
		RelationshipPojo relationship = new RelationshipPojo(0, ISA, "404684003", "STATED_RELATIONSHIP");
		AxiomPojo axiom = new AxiomPojo();
		axiom.setAxiomId("ax-1");
		axiom.setRelationships(new HashSet<>(Set.of(relationship)));
		concept.setClassAxioms(Set.of(axiom));
		return concept;
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
		ArrayNode array = objectMapper.createArrayNode();
		for (JsonNode concept : concepts) {
			array.add(concept);
		}
		return array;
	}

	private static ObjectNode blockingCrsConcept(String conceptId, String crsId, boolean saved, boolean isNewConcept,
			String fsn) {
		ObjectNode node = objectMapper.createObjectNode();
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
