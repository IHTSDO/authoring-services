package org.ihtsdo.authoringservices.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.sf.json.JSONObject;
import org.ihtsdo.authoringservices.domain.CrsBlockingState;
import org.ihtsdo.authoringservices.domain.CrsBlockingState.BlockingConcept;
import org.ihtsdo.authoringservices.domain.UiConfiguration;
import org.ihtsdo.authoringservices.service.client.ContentRequestServiceClient;
import org.ihtsdo.authoringservices.service.client.ContentRequestServiceClient.ContentRequestDto;
import org.ihtsdo.authoringservices.service.client.ContentRequestServiceClientFactory;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
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
import java.util.List;
import java.util.Map;

@Service
public class ContentRequestService {

	static final String CRS_ENDPOINT = "crsEndpoint";
	static final String CRS_ENDPOINT_US = "crsEndpoint.US";
	private static final String DEFAULT_MODULE_ID = "defaultModuleId";
	private static final String EXTENSION_BRANCH_PREFIX = "MAIN/SNOMEDCT-";
	private static final String SHARED = "SHARED";
	private static final String CRS_CONCEPTS_PANEL = "crs-concepts";
	private static final String CONCEPT_ID = "conceptId";

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

	public ObjectNode applyRequest(String projectKey, String taskKey, String requestId) throws BusinessServiceException {
		if (!StringUtils.hasText(requestId)) {
			throw new IllegalArgumentException("Parameter requestId is required.");
		}
		permissionService.checkFullPermissionOnProjectOrThrow(projectKey);

		String branchPath = branchService.getTaskBranchPathUsingCache(projectKey, taskKey);
		ContentRequestDto crsRequest = fetchCrsRequest(branchPath, requestId);
		JsonNode crsConcept = parseCrsConcept(crsRequest);
		JsonNode existingConcept = null;
		if (requiresExistingConcept(crsConcept)) {
			existingConcept = fetchExistingConcept(branchPath, textOrNull(crsConcept, CONCEPT_ID));
		}
		return crsConceptPreparation.prepareCrsConcept(crsConcept, existingConcept, defaultModuleId(branchPath));
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
