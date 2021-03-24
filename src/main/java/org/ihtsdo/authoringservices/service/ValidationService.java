package org.ihtsdo.authoringservices.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import org.ihtsdo.otf.jms.MessagingHelper;
import org.ihtsdo.otf.rest.client.orchestration.OrchestrationRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.PathHelper;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.authoringservices.domain.ReleaseRequest;
import org.ihtsdo.authoringservices.domain.Status;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.jms.JMSException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
public class ValidationService {

	private static final String VALIDATION_REQUEST_QUEUE = "orchestration.termserver-release-validation";
	public static final String VALIDATION_RESPONSE_QUEUE = "sca.termserver-release-validation.response";
	public static final String PATH = "path";
	public static final String USERNAME = "username";
	public static final String X_AUTH_TOKEN = "X-AUTH-token";
	public static final String PROJECT = "project";
	public static final String TASK = "task";
	public static final String EFFECTIVE_TIME = "effective-time";
	public static final String STATUS = "status";
	public static final String STATUS_SCHEDULED = "SCHEDULED";
	public static final String STATUS_COMPLETE = "COMPLETED";
	public static final String STATUS_NOT_TRIGGERED = "NOT_TRIGGERED";
	public static final String ASSERTION_GROUP_NAMES = "assertionGroupNames";
	public static final String RVF_DROOLS_ASSERTION_GROUP_NAMES = "rvfDroolsAssertionGroupNames";
	public static final String PREVIOUS_RELEASE = "previousRelease";
	public static final String DEPENDENCY_RELEASE = "dependencyRelease";
	public static final String SHORT_NAME ="shortname";
	public static final String PREVIOUS_PACKAGE = "previousPackage";
	public static final String DEPENDENCY_PACKAGE = "dependencyPackage";
	public static final String DEFAULT_MODULE_ID = "defaultModuleId";
	public static final String ENABLE_MRCM_VALIDATION = "enableMRCMValidation";

	@Value("${orchestration.name}")
	private String orchestrationName;

	@Autowired
	private TaskService taskService;

	@Autowired
	private MessagingHelper messagingHelper;

	@Autowired
	private OrchestrationRestClient orchestrationRestClient;

	@Autowired
	private BranchService branchService;

	private LoadingCache<String, String> validationStatusLoadingCache;

	private Logger logger = LoggerFactory.getLogger(getClass());

	@PostConstruct
	public void init() {
		validationStatusLoadingCache = CacheBuilder.newBuilder()
				.maximumSize(10000)
				.build(
						new CacheLoader<String, String>() {
							public String load(String path) throws Exception {
								return getValidationStatusesWithoutCache(Collections.singletonList(path)).iterator().next();
							}

							@Override
							public Map<String, String> loadAll(Iterable<? extends String> paths) throws Exception {
								final ImmutableMap.Builder<String, String> map = ImmutableMap.builder();
								List<String> pathsToLoad = new ArrayList<>();
								for (String path : paths) {
									final String status = validationStatusLoadingCache.getIfPresent(path);
									if (status != null) {
										map.put(path, status);
									} else {
										pathsToLoad.add(path);
									}
								}
								if (!pathsToLoad.isEmpty()) {
									final List<String> validationStatuses = getValidationStatusesWithoutCache(pathsToLoad);
									if (validationStatuses != null && validationStatuses.size() == pathsToLoad.size()) {
										for (int i = 0; i < pathsToLoad.size(); i++) {
											String value = validationStatuses.get(i);
											if (value == null) {
												value = STATUS_NOT_TRIGGERED;
											}
											map.put(pathsToLoad.get(i), value);
										}
									} else {
										logger.error("Unable to load Validation Status, {} requested none returned, see logs", pathsToLoad.size());
									}
								}
								return map.build();
							}
						});
	}

	void updateValidationStatusCache(String path, String validationStatus) {
		logger.info("Cache value before '{}'", validationStatusLoadingCache.getIfPresent(path));
		validationStatusLoadingCache.put(path, validationStatus);
		logger.info("Cache value after '{}'", validationStatusLoadingCache.getIfPresent(path));

	}

	public Status startValidation(String projectKey, String taskKey, String username, String authenticationToken) throws BusinessServiceException {
		// MRCM validation is skipped for task validation
		return doStartValidation(taskService.getTaskBranchPathUsingCache(projectKey, taskKey), username, authenticationToken, projectKey, taskKey, null, false);
	}

	public Status startValidation(String projectKey, String username, String authenticationToken) throws BusinessServiceException {
		return doStartValidation(taskService.getProjectBranchPathUsingCache(projectKey), username, authenticationToken, projectKey, null, null, true);
	}

	private Status doStartValidation(String path, String username, String authenticationToken, String projectKey, String taskKey, String effectiveTime, boolean enableMRCMValidation) throws BusinessServiceException {
		try {
			final Map<String, Object> mergedBranchMetadata = branchService.getBranchMetadataIncludeInherited(path);
			Map<String, Object> properties = new HashMap<>();
			copyProperty(ASSERTION_GROUP_NAMES, mergedBranchMetadata, properties);
			copyProperty(RVF_DROOLS_ASSERTION_GROUP_NAMES, mergedBranchMetadata, properties);
			copyProperty(PREVIOUS_RELEASE, mergedBranchMetadata, properties);
			copyProperty(DEPENDENCY_RELEASE, mergedBranchMetadata, properties);
			copyProperty(SHORT_NAME, mergedBranchMetadata, properties);
			copyProperty(PREVIOUS_PACKAGE, mergedBranchMetadata, properties);
			copyProperty(DEPENDENCY_PACKAGE, mergedBranchMetadata, properties);
			copyProperty(DEFAULT_MODULE_ID, mergedBranchMetadata, properties);
			properties.put(PATH, path);
			properties.put(USERNAME, username);
			properties.put(X_AUTH_TOKEN, authenticationToken);
			properties.put(ENABLE_MRCM_VALIDATION, enableMRCMValidation);
			if (projectKey != null) {
				properties.put(PROJECT, projectKey);
			}
			if (taskKey != null) {
				properties.put(TASK, taskKey);
			}
			if (effectiveTime != null) {
				properties.put(EFFECTIVE_TIME, effectiveTime);
			}
			String prefix = orchestrationName + ".";
			messagingHelper.send(prefix + VALIDATION_REQUEST_QUEUE, "", properties, prefix + VALIDATION_RESPONSE_QUEUE);
			validationStatusLoadingCache.put(path, STATUS_SCHEDULED);
			return new Status(STATUS_SCHEDULED);
		} catch (JsonProcessingException | JMSException e) {
			throw new BusinessServiceException("Failed to send validation request, please contact support.", e);
		} catch (ServiceException e) {
			throw new BusinessServiceException("Failed to read branch information, validation request not sent.", e);
		}
	}

	private void copyProperty(String key, Map<String, Object> metadata, Map<String, Object> properties) {
		final String value = (String) metadata.get(key);
		if (!Strings.isNullOrEmpty(value)) {
			properties.put(key, value);
		}
	}

	public String getValidationJson(String projectKey, String taskKey) throws BusinessServiceException {
		return getValidationJsonIfAvailable(taskService.getTaskBranchPathUsingCache(projectKey, taskKey));
	}

	public String getValidationJson(String projectKey) throws BusinessServiceException {
		return getValidationJsonIfAvailable(taskService.getProjectBranchPathUsingCache(projectKey));
	}
	
	public String getValidationJson() throws BusinessServiceException {
		return getValidationJsonIfAvailable(PathHelper.getMainPath());
	}
	
	private String getValidationJsonIfAvailable(String path) throws BusinessServiceException {
		try {
		//Only return the validation json if the validation is complete
			String validationStatus = getValidationStatus(path);
			if (STATUS_COMPLETE.equals(validationStatus)) {
				return orchestrationRestClient.retrieveValidation(path);
			} else {
				logger.warn("Ignoring request for validation json for path {} as status {} ", path, validationStatus);
				return null;
			}
		} catch (Exception e) {
			throw new BusinessServiceException ("Unable to recover validation json for " + path, e);
		}
	}

	public ImmutableMap<String, String> getValidationStatuses(Collection<String> paths) throws ExecutionException {
		return validationStatusLoadingCache.getAll(paths);
	}
	
	public String getValidationStatus(String path) throws ExecutionException {
		return validationStatusLoadingCache.get(path);
	}

	private List<String> getValidationStatusesWithoutCache(List<String> paths) {
		List<String> statuses = null;
		try {
			statuses = orchestrationRestClient.retrieveValidationStatuses(paths);
		} catch (Exception e) {
			logger.error("Failed to retrieve validation status of tasks {}", paths, e);
		}
		if (statuses == null) {
			statuses = new ArrayList<>();
			for (int i = 0; i < paths.size(); i++) {
				statuses.add(TaskService.FAILED_TO_RETRIEVE);
			}
		}
		for (int i = 0; i < statuses.size(); i++) {
			if (statuses.get(i) == null) {
				statuses.set(i, STATUS_NOT_TRIGGERED);
			}
		}
		return statuses;
	}

	//Start validation for MAIN
	public Status startValidation(ReleaseRequest releaseRequest,
			String username, String authenticationToken) throws BusinessServiceException {
		String effectiveDate = null;
		if (releaseRequest != null && releaseRequest.getEffectiveDate() != null) {
			String potentialEffectiveDate = releaseRequest.getEffectiveDate();
			try {
				
				new SimpleDateFormat("yyyyDDmm").parse(potentialEffectiveDate);
				effectiveDate = potentialEffectiveDate;
			} catch (ParseException e) {
				logger.error("Unable to set effective date for MAIN validation, unrecognised: " + potentialEffectiveDate, e);
			}
		}
		return doStartValidation(PathHelper.getMainPath(), username, authenticationToken, null, null, effectiveDate, false);
	}

	public void clearStatusCache() {
		validationStatusLoadingCache.invalidateAll();
	}
}
