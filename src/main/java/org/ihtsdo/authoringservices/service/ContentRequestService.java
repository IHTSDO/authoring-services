package org.ihtsdo.authoringservices.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.sf.json.JSONObject;
import org.ihtsdo.authoringservices.domain.CrsBlockingState;
import org.ihtsdo.authoringservices.domain.CrsBlockingState.BlockingConcept;
import org.ihtsdo.authoringservices.domain.ContentRequestResult;
import org.ihtsdo.authoringservices.domain.UiConfiguration;
import org.ihtsdo.authoringservices.service.client.ContentRequestServiceClient;
import org.ihtsdo.authoringservices.service.client.ContentRequestServiceClient.ContentRequestDto;
import org.ihtsdo.authoringservices.service.client.ContentRequestServiceClientFactory;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.CodeSystem;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.CodeSystemVersion;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ConceptMiniPojo;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ConceptPojo;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ContentRequestService {

	static final String CRS_ENDPOINT = "crsEndpoint";
	static final String CRS_ENDPOINT_US = "crsEndpoint.US";
	static final String CONTENT_PROMOTION_TOPIC = "Content Promotion";
	private static final String DEFAULT_MODULE_ID = "defaultModuleId";
	private static final String EXTENSION_BRANCH_PREFIX = "MAIN/SNOMEDCT-";
	private static final String SHARED = "SHARED";
	private static final String CRS_CONCEPTS_PANEL = "crs-concepts";
	private static final String CONCEPT_ID = "conceptId";
	private static final String BATCH_CHANGE_FLAG = "batch-change";
	private static final int EXISTING_CONCEPT_SEARCH_LIMIT = 1000;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final PermissionService permissionService;
	private final BranchService branchService;
	private final SnowstormRestClientFactory snowstormRestClientFactory;
	private final ContentRequestServiceClientFactory contentRequestServiceClientFactory;
	private final UiConfiguration uiConfiguration;
	private final CrsConceptPreparation crsConceptPreparation;
	private final UiStateService uiStateService;
	private final ObjectMapper objectMapper;

	public ContentRequestService(PermissionService permissionService,
			BranchService branchService,
			SnowstormRestClientFactory snowstormRestClientFactory,
			ContentRequestServiceClientFactory contentRequestServiceClientFactory,
			UiConfiguration uiConfiguration,
			CrsConceptPreparation crsConceptPreparation,
			UiStateService uiStateService,
			ObjectMapper objectMapper) {
		this.permissionService = permissionService;
		this.branchService = branchService;
		this.snowstormRestClientFactory = snowstormRestClientFactory;
		this.contentRequestServiceClientFactory = contentRequestServiceClientFactory;
		this.uiConfiguration = uiConfiguration;
		this.crsConceptPreparation = crsConceptPreparation;
		this.uiStateService = uiStateService;
		this.objectMapper = objectMapper;
	}

	public JsonNode applyRequest(String projectKey, String taskKey, String requestId) throws BusinessServiceException {
		if (!StringUtils.hasText(requestId)) {
			throw new IllegalArgumentException("Parameter requestId is required.");
		}
		permissionService.checkFullPermissionOnProjectOrThrow(projectKey);

		String branchPath = branchService.getTaskBranchPathUsingCache(projectKey, taskKey);
		try {
			branchService.createBranchIfNeeded(branchPath);
		} catch (ServiceException e) {
			throw new BusinessServiceException("Failed to create branch " + branchPath, e);
		}

		ContentRequestDto crsRequest = fetchCrsRequest(branchPath, requestId);
		JsonNode crsConcept = parseCrsConcept(crsRequest);
		if (isContentPromotion(crsConcept)) {
			return toResponse(donateContentPromotion(branchPath, crsRequest, crsConcept));
		}
		JsonNode existingConcept = null;
		if (requiresExistingConcept(crsConcept)) {
			existingConcept = fetchExistingConcept(branchPath, textOrNull(crsConcept, CONCEPT_ID));
		}
		return toResponse(crsConceptPreparation.prepareCrsConcept(crsConcept, existingConcept, defaultModuleId(branchPath)));
	}

	private ContentRequestResult donateContentPromotion(String branchPath, ContentRequestDto crsRequest, JsonNode crsConcept)
			throws BusinessServiceException {
		String organization = crsRequest.getOrganizationOrNull();
		if (!StringUtils.hasText(organization)) {
			throw new IllegalArgumentException("Could not find code system for " + organization);
		}

		SnowstormRestClient snowstormRestClient = snowstormRestClientFactory.getClient();
		CodeSystem codeSystem;
		try {
			codeSystem = snowstormRestClient.getCodeSystem(organization);
		} catch (RestClientException e) {
			throw new BusinessServiceException("Failed to load code system " + organization, e);
		}
		if (codeSystem == null) {
			throw new IllegalArgumentException("Could not find code system for " + organization);
		}
		CodeSystemVersion latestVersion = codeSystem.getLatestVersion();
		if (latestVersion == null || !StringUtils.hasText(latestVersion.getBranchPath())) {
			throw new IllegalArgumentException("The latest version not found against code system " + organization);
		}

		try {
			snowstormRestClient.setAuthorFlag(branchPath, BATCH_CHANGE_FLAG, "true");
		} catch (RestClientException e) {
			throw new BusinessServiceException("Failed to set author flag on branch " + branchPath, e);
		}

		String summary = textOrNull(crsConcept.path(CrsConceptPreparation.DEFINITION_OF_CHANGES), "summary");
		List<String> conceptIdsToCopy = CrsConceptPreparation.getConceptIdsFromPromotionSummary(summary);
		if (conceptIdsToCopy.isEmpty()) {
			throw new IllegalArgumentException("No concepts found in the content promotion summary.");
		}

		String donatedConceptId = textOrNull(crsConcept, CONCEPT_ID);
		if (!StringUtils.hasText(donatedConceptId)) {
			throw new IllegalArgumentException("CRS request is missing conceptId.");
		}
		ContentRequestResult alreadyPresent = existingConceptsIfPresent(
				snowstormRestClient, branchPath, conceptIdsToCopy, donatedConceptId);
		if (alreadyPresent != null) {
			return alreadyPresent;
		}

		boolean includeDependencies = conceptIdsToCopy.size() > 1;
		List<String> copiedIds;
		try {
			copiedIds = copiedConceptIds(snowstormRestClient.copyConcepts(
					branchPath, latestVersion.getBranchPath(), donatedConceptId, includeDependencies));
		} catch (RestClientException e) {
			throw new BusinessServiceException("Failed to copy concepts onto branch " + branchPath, e);
		}
		return ContentRequestResult.of(donationConceptsJson(
				loadCopiedConcepts(snowstormRestClient, branchPath, copiedIds), conceptIdsToCopy, copiedIds));
	}

	private ContentRequestResult existingConceptsIfPresent(SnowstormRestClient snowstormRestClient, String branchPath,
			List<String> conceptIdsToCopy, String donatedConceptId) throws BusinessServiceException {
		Set<ConceptMiniPojo> existing;
		try {
			int limit = Math.max(EXISTING_CONCEPT_SEARCH_LIMIT, conceptIdsToCopy.size());
			existing = snowstormRestClient.getConceptMinis(branchPath, conceptIdsToCopy, limit, null);
		} catch (RestClientException e) {
			throw new BusinessServiceException("Failed to search concepts on branch " + branchPath, e);
		}

		Map<String, ConceptMiniPojo> existingById = new LinkedHashMap<>();
		if (existing != null) {
			for (ConceptMiniPojo concept : existing) {
				if (concept != null && StringUtils.hasText(concept.getConceptId())) {
					existingById.put(concept.getConceptId(), concept);
				}
			}
		}

		String foundDonatedConcept = null;
		List<String> foundDependentConcepts = new ArrayList<>();
		List<String> foundIds = new ArrayList<>();
		for (String conceptId : conceptIdsToCopy) {
			ConceptMiniPojo match = existingById.get(conceptId);
			if (match == null) {
				continue;
			}
			foundIds.add(conceptId);
			String idAndFsnTerm = idAndFsnTerm(match);
			if (conceptId.equals(donatedConceptId)) {
				foundDonatedConcept = idAndFsnTerm;
			} else {
				foundDependentConcepts.add(idAndFsnTerm);
			}
		}

		if (foundDonatedConcept == null && foundDependentConcepts.isEmpty()) {
			return null;
		}
		JsonNode concepts = donationConceptsJson(
				loadCopiedConcepts(snowstormRestClient, branchPath, foundIds), conceptIdsToCopy, foundIds);
		if (foundDonatedConcept != null) {
			return ContentRequestResult.error(concepts, donatedConceptExistsMessage(foundDonatedConcept, branchPath));
		}
		return ContentRequestResult.warning(concepts,
				dependentConceptsExistMessage(foundDependentConcepts, branchPath));
	}

	private JsonNode toResponse(JsonNode concept) {
		return objectMapper.valueToTree(ContentRequestResult.of(withSavedFlag(conceptsJson(concept), false)));
	}

	private JsonNode toResponse(ContentRequestResult result) {
		return objectMapper.valueToTree(result);
	}

	private JsonNode conceptsJson(JsonNode concept) {
		return objectMapper.createArrayNode().add(concept);
	}

	private JsonNode conceptsJson(List<ConceptPojo> concepts) {
		return objectMapper.valueToTree(concepts != null ? concepts : List.of());
	}

	private JsonNode donationConceptsJson(List<ConceptPojo> concepts, List<String> conceptIdsToCopy,
			List<String> presentConceptIds) {
		return withSavedFlag(conceptsJson(concepts), allConceptsSaved(conceptIdsToCopy, presentConceptIds));
	}

	private JsonNode withSavedFlag(JsonNode concepts, boolean saved) {
		if (!(concepts instanceof ArrayNode array)) {
			return concepts;
		}
		ArrayNode result = objectMapper.createArrayNode();
		for (JsonNode concept : array) {
			if (concept instanceof ObjectNode objectNode) {
				ObjectNode copy = objectNode.deepCopy();
				copy.put("saved", saved);
				result.add(copy);
			} else {
				result.add(concept);
			}
		}
		return result;
	}

	private static boolean allConceptsSaved(List<String> conceptIdsToCopy, List<String> presentConceptIds) {
		if (conceptIdsToCopy == null || conceptIdsToCopy.isEmpty() || presentConceptIds == null) {
			return false;
		}
		return presentConceptIds.containsAll(conceptIdsToCopy);
	}

	private static List<String> copiedConceptIds(List<ConceptMiniPojo> copied) {
		List<String> copiedIds = new ArrayList<>();
		if (copied == null) {
			return copiedIds;
		}
		for (ConceptMiniPojo mini : copied) {
			if (mini != null && StringUtils.hasText(mini.getConceptId())) {
				copiedIds.add(mini.getConceptId());
			}
		}
		return copiedIds;
	}

	private List<ConceptPojo> loadCopiedConcepts(SnowstormRestClient snowstormRestClient, String branchPath,
			List<String> copiedIds) throws BusinessServiceException {
		if (copiedIds == null || copiedIds.isEmpty()) {
			return List.of();
		}
		List<ConceptPojo> fetched;
		try {
			fetched = snowstormRestClient.searchConcepts(branchPath, copiedIds);
		} catch (RestClientException e) {
			throw new BusinessServiceException("Failed to load copied concepts from branch " + branchPath, e);
		}
		Map<String, ConceptPojo> byId = new LinkedHashMap<>();
		if (fetched != null) {
			for (ConceptPojo concept : fetched) {
				if (concept != null && StringUtils.hasText(concept.getConceptId())) {
					byId.put(concept.getConceptId(), concept);
				}
			}
		}
		List<ConceptPojo> ordered = new ArrayList<>();
		for (String copiedId : copiedIds) {
			ConceptPojo concept = byId.get(copiedId);
			if (concept != null) {
				ordered.add(concept);
			}
		}
		return ordered;
	}

	static String idAndFsnTerm(ConceptMiniPojo concept) {
		String conceptId = concept.getConceptId();
		String fsnTerm = fsnTerm(concept);
		if (!StringUtils.hasLength(fsnTerm)) {
			return conceptId;
		}
		return conceptId + " | " + fsnTerm + " |";
	}

	static String donatedConceptExistsMessage(String idAndFsnTerm, String destinationBranch) {
		return "The donated concept " + idAndFsnTerm + " already exists in this task (" + destinationBranch
				+ ") and can not be created again. Please reject the request.";
	}

	static String dependentConceptsExistMessage(List<String> foundDependentConcepts, String destinationBranch) {
		String base = " in this task (" + destinationBranch
				+ ") and can not be created again. The request should be submitted again without dependencies.";
		if (foundDependentConcepts.size() == 1) {
			return "The dependent concept " + foundDependentConcepts.get(0) + " already exists" + base;
		}
		if (foundDependentConcepts.size() == 2) {
			return "The dependent concepts " + foundDependentConcepts.get(0) + " and " + foundDependentConcepts.get(1)
					+ " already exist" + base;
		}
		String last = foundDependentConcepts.get(foundDependentConcepts.size() - 1);
		String rest = String.join(", ", foundDependentConcepts.subList(0, foundDependentConcepts.size() - 1));
		return "The dependent concepts " + rest + ", and " + last + " already exist" + base;
	}

	private static String fsnTerm(ConceptMiniPojo concept) {
		if (concept.getFsn() != null && StringUtils.hasLength(concept.getFsn().getTerm())) {
			return concept.getFsn().getTerm();
		}
		return concept.getFsnTerm();
	}

	private ContentRequestDto fetchCrsRequest(String branchPath, String requestId) throws BusinessServiceException {
		String endpointKey = isInternationalBranch(branchPath) ? CRS_ENDPOINT : CRS_ENDPOINT_US;
		String crsUrl = crsEndpointUrl(endpointKey);
		ContentRequestServiceClient crsClient = contentRequestServiceClientFactory.getClient(crsUrl);
		try {
			ContentRequestDto dto = crsClient.getRequestDetails(requestId);
			if (dto == null) {
				throw new ResourceNotFoundException("CRS request", requestId);
			}
			return dto;
		} catch (HttpClientErrorException.NotFound _) {
			throw new ResourceNotFoundException("CRS request", requestId);
		} catch (HttpStatusCodeException e) {
			throw new BusinessServiceException("Failed to fetch CRS request " + requestId, e);
		}
	}

	private JsonNode fetchExistingConcept(String branchPath, String conceptId) throws BusinessServiceException {
		try {
			SnowstormRestClient client = snowstormRestClientFactory.getClient();
			ConceptPojo concept = client.getConcept(branchPath, conceptId);
			if (concept == null) {
				throw new ResourceNotFoundException("Concept " + conceptId + " not found on branch " + branchPath);
			}
			JsonNode tree = objectMapper.valueToTree(concept);
			if (!(tree instanceof ObjectNode)) {
				throw new BusinessServiceException("Failed to convert concept " + conceptId + " to JSON");
			}
			return tree;
		} catch (ResourceNotFoundException e) {
			throw e;
		} catch (RestClientException e) {
			throw new BusinessServiceException("Failed to load concept " + conceptId + " from branch " + branchPath, e);
		}
	}

	private String defaultModuleId(String branchPath) throws BusinessServiceException {
		try {
			Map<String, Object> metadata = branchService.getBranchMetadataIncludeInherited(branchPath);
			if (metadata == null) {
				return null;
			}
			Object value = metadata.get(DEFAULT_MODULE_ID);
			return value != null ? value.toString() : null;
		} catch (ServiceException e) {
			throw new BusinessServiceException("Failed to read branch metadata for " + branchPath, e);
		}
	}

	private String crsEndpointUrl(String endpointKey) throws BusinessServiceException {
		if (uiConfiguration.getEndpoints() == null) {
			throw new BusinessServiceException("CRS endpoint is not configured for " + endpointKey);
		}
		String crsUrl = uiConfiguration.getEndpoints().get(endpointKey);
		if (!StringUtils.hasLength(crsUrl)) {
			throw new BusinessServiceException("CRS endpoint is not configured for " + endpointKey);
		}
		return crsUrl;
	}

	private JsonNode parseCrsConcept(ContentRequestDto crsRequest) throws BusinessServiceException {
		JSONObject concept = crsRequest.getConcept();
		if (concept == null || concept.isEmpty()) {
			return null;
		}
		try {
			return objectMapper.readTree(concept.toString());
		} catch (JsonProcessingException e) {
			throw new BusinessServiceException("Failed to parse CRS request concept JSON", e);
		}
	}

	private static boolean isContentPromotion(JsonNode crsConcept) {
		if (crsConcept == null || !crsConcept.isObject()) {
			return false;
		}
		return CONTENT_PROMOTION_TOPIC.equals(
				crsConcept.path(CrsConceptPreparation.DEFINITION_OF_CHANGES).path("topic").asText(null));
	}

	private static boolean requiresExistingConcept(JsonNode crsConcept) {
		if (crsConcept == null || !crsConcept.isObject()) {
			return false;
		}
		if (CrsConceptPreparation.isNewConcept(crsConcept)) {
			return false;
		}
		return StringUtils.hasText(textOrNull(crsConcept, CONCEPT_ID));
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

	public List<String> formatBlockingConcepts(List<BlockingConcept> blockingConcepts) {
		return blockingConcepts.stream()
				.map(concept -> concept.conceptId() + " (Request ID: " + concept.crsRequestId() + ")")
				.toList();
	}

	static boolean isInternationalBranch(String branchPath) {
		return branchPath == null || !branchPath.startsWith(EXTENSION_BRANCH_PREFIX);
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

	private static BlockingConcept toBlockingConceptIfApplicable(JsonNode crsConcept) {
		boolean saved = crsConcept.path("saved").asBoolean(false);
		boolean isNewConcept = crsConcept.path("isNewConcept").asBoolean(false);
		String conceptId = textOrNull(crsConcept, CONCEPT_ID);
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

	private static String textOrEmpty(JsonNode node, String field) {
		String value = textOrNull(node, field);
		return value != null ? value : "";
	}

	private static String textOrNull(JsonNode node, String field) {
		if (node == null) {
			return null;
		}
		String text = node.path(field).asText(null);
		if (!StringUtils.hasLength(text)) {
			return null;
		}
		String trimmed = text.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
