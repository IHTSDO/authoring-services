package org.ihtsdo.authoringservices.service;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;
import org.ihtsdo.authoringservices.domain.ReleaseRequest;
import org.ihtsdo.authoringservices.domain.Status;
import org.ihtsdo.authoringservices.domain.ValidationConfiguration;
import org.ihtsdo.authoringservices.domain.ValidationJobStatus;
import org.ihtsdo.authoringservices.entity.Validation;
import org.ihtsdo.authoringservices.repository.ValidationRepository;
import org.ihtsdo.authoringservices.service.dao.SRSFileDAO;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.otf.dao.s3.S3ClientImpl;
import org.ihtsdo.otf.rest.client.terminologyserver.PathHelper;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Branch;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.otf.rest.exception.EntityAlreadyExistsException;
import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;
import org.ihtsdo.otf.utils.DateUtils;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;
import java.io.*;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ValidationService {

	private Logger logger = LoggerFactory.getLogger(getClass());

	public static final String VALIDATION_STATUS = "validationStatus";
	public static final String REPORT_URL = "reportUrl";
	public static final String DAILY_BUILD_REPORT_URL = "dailyBuildReportUrl";
	public static final String CONTENT_HEAD_TIMESTAMP = "contentHeadTimestamp";
	public static final String RUN_ID = "runId";
	public static final String PROJECT_KEY = "projectKey";
	public static final String TASK_KEY = "taskKey";
	public static final String VALIDATION_START_TIMESTAMP = "startTimestamp";
	public static final String VALIDATION_END_TIMESTAMP = "endTimestamp";

	public static final String VALIDATION_RESPONSE_QUEUE = "termserver-release-validation.response";
	public static final String ASSERTION_GROUP_NAMES = "assertionGroupNames";
	public static final String DISABLE_TRACEABILITY_VALIDATION = "disableTraceabilityValidation";
	public static final String ENABLE_DROOLS_VALIDATION = "enableDroolsInRVF";
	public static final String PREVIOUS_RELEASE = "previousRelease";
	public static final String DEPENDENCY_RELEASE = "dependencyRelease";
	public static final String SHORT_NAME ="shortname";
	public static final String PREVIOUS_PACKAGE = "previousPackage";
	public static final String DEPENDENCY_PACKAGE = "dependencyPackage";
	public static final String DEFAULT_MODULE_ID = "defaultModuleId";
	public static final String EXPECTED_EXTENSION_MODULES = "expectedExtensionModules";
	public static final String INTERNATIONAL = "international";
	public static final String MAX_FAILURE_EXPORT = "failureExportMax";

	@Value("${aws.resources.enabled}")
	private boolean awsResourceEnabled;

	@Value("${aws.key}")
	private String accessKey;

	@Value("${aws.secretKey}")
	private String secretKey;

	@Value("${aws.s3.spell-check.bucket}")
	private String bucket;

	@Value("${aws.s3.technical-issue-items.path}")
	private String techinicalIssueItemsPath;

	@Value("${aws.s3.semantic-tag-items.path}")
	private String semanticTagItemsPath;

	@Value("${rvf.url}")
	private String rvfUrl;

	@Value("${sca.jms.queue.prefix}")
	private String scaQueuePrefix;

	@Autowired
	private TaskService taskService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private ValidationRepository validationRepository;

	@Autowired
	private SnowstormRestClientFactory snowstormRestClientFactory;

	@Autowired
	protected SRSFileDAO srsDAO;

	private final RestTemplate rvfRestTemplate;

	private final ObjectMapper objectMapper;

	private LoadingCache<String, Validation> validationLoadingCache;

	private Set<String> technicalItems;

	public ValidationService() {
		this.rvfRestTemplate = new RestTemplate();
		this.objectMapper = Jackson2ObjectMapperBuilder.json().failOnUnknownProperties(false).build();
	}

	@PostConstruct
	public void init() {
		validationLoadingCache = CacheBuilder.newBuilder()
				.maximumSize(10000)
				.build(
                        new CacheLoader<>() {
                            public Validation load(String path) throws Exception {
                                return getValidationStatusesWithoutCache(Collections.singletonList(path)).get(path);
                            }

                            @Override
                            public Map<String, Validation> loadAll(Iterable<? extends String> paths) throws Exception {
                                final ImmutableMap.Builder<String, Validation> map = ImmutableMap.builder();
                                List<String> pathsToLoad = new ArrayList<>();
                                for (String path : paths) {
                                    final Validation validation = validationLoadingCache.getIfPresent(path);
                                    if (validation != null) {
                                        map.put(path, validation);
                                    } else {
                                        pathsToLoad.add(path);
                                    }
                                }
                                if (!pathsToLoad.isEmpty()) {
                                    final Map<String, Validation> validationMap = getValidationStatusesWithoutCache(pathsToLoad);
                                    if (validationMap != null) {
                                        map.putAll(validationMap);
                                    }
                                }
                                return map.build();
                            }
                        });
		this.technicalItems = new HashSet<>();
		if (this.awsResourceEnabled) {
		 	S3ClientImpl s3Client = new S3ClientImpl(new BasicAWSCredentials(accessKey, secretKey));
			if (s3Client.doesObjectExist(this.bucket, this.techinicalIssueItemsPath)) {
				S3ObjectInputStream objectContent = s3Client.getObject(bucket, techinicalIssueItemsPath).getObjectContent();
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(objectContent))) {
					String line;
					while ((line = reader.readLine()) != null) {
						this.technicalItems.add(line);
					}
				} catch (IOException e) {
					logger.error("Failed to load technical assertion list from S3", e);
				}
			}
		}
	}

	public void updateValidationCache(final String branchPath, final Map<String, String> newPropertyValues) {
		Validation validation = validationRepository.findByBranchPath(branchPath);
		if (validation == null) {
			validation = new Validation(branchPath);
		}
		if (newPropertyValues.containsKey(VALIDATION_STATUS)) {
			validation.setStatus(newPropertyValues.get(VALIDATION_STATUS));
		}
		if (newPropertyValues.containsKey(REPORT_URL)) {
			validation.setReportUrl(newPropertyValues.get(REPORT_URL));
		}
		if (newPropertyValues.containsKey(DAILY_BUILD_REPORT_URL)) {
			validation.setDailyBuildReportUrl(newPropertyValues.get(DAILY_BUILD_REPORT_URL));
		}
		if (newPropertyValues.containsKey(CONTENT_HEAD_TIMESTAMP)) {
			validation.setContentHeadTimestamp(Long.valueOf(newPropertyValues.get(CONTENT_HEAD_TIMESTAMP)));
		}
		if (newPropertyValues.containsKey(RUN_ID)) {
			validation.setRunId(Long.valueOf(newPropertyValues.get(RUN_ID)));
		}
		if (newPropertyValues.containsKey(PROJECT_KEY)) {
			validation.setProjectKey(newPropertyValues.get(PROJECT_KEY));
		}
		if (newPropertyValues.containsKey(TASK_KEY)) {
			validation.setTaskKey(newPropertyValues.get(TASK_KEY));
		}
		if (newPropertyValues.containsKey(VALIDATION_START_TIMESTAMP)) {
			validation.setStartTimestamp(newPropertyValues.get(VALIDATION_START_TIMESTAMP) != null ? Long.valueOf(newPropertyValues.get(VALIDATION_START_TIMESTAMP)) : null);
		}
		if (newPropertyValues.containsKey(VALIDATION_END_TIMESTAMP)) {
			validation.setEndTimestamp(newPropertyValues.get(VALIDATION_END_TIMESTAMP) != null ? Long.valueOf(newPropertyValues.get(VALIDATION_END_TIMESTAMP)) : null);
		}

		validation = validationRepository.save(validation);
		validationLoadingCache.put(branchPath, validation);
	}

    //Start validation for MAIN
    public Status startValidation(ReleaseRequest releaseRequest) throws BusinessServiceException {
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
        return doStartValidation(PathHelper.getMainPath(),null,null, effectiveDate,false);
    }

	public Status startValidation(String branch, boolean enableMRCMValidation) throws BusinessServiceException {
		return doStartValidation(branch, null, null, null, enableMRCMValidation);
	}

	public Status startValidation(String projectKey, String taskKey, boolean enableMRCMValidation) throws BusinessServiceException {
		String branchPath = taskKey != null ? taskService.getTaskBranchPathUsingCache(projectKey, taskKey) : taskService.getProjectBranchPathUsingCache(projectKey);
	    return doStartValidation(branchPath, projectKey, taskKey, null, enableMRCMValidation);
	}

	private synchronized Status doStartValidation(String branchPath, String projectKey, String taskKey, String effectiveDate, boolean enableMRCMValidation) throws BusinessServiceException {
		try {
			final String username = SecurityUtil.getUsername();
		    final String authToken = SecurityUtil.getAuthenticationToken();
			final Map<String, Object> branchMetadata = branchService.getBranchMetadataIncludeInherited(branchPath);
			ValidationConfiguration validationConfig = constructValidationConfig(branchPath, branchMetadata, effectiveDate, enableMRCMValidation, projectKey, taskKey);
			Validation validation = getValidation(branchPath);
			if (validation.getStatus() != null && !ValidationJobStatus.isAllowedTriggeringState(validation.getStatus())) {
				throw new EntityAlreadyExistsException("An in-progress validation has been detected for " + branchPath + " at state " + validation.getStatus());
			}
			new Thread(new ValidationRunner(validationConfig, snowstormRestClientFactory.getClient(), srsDAO, this, notificationService, rvfUrl, scaQueuePrefix, username, authToken)).start();

			Map<String, String> newPropertyValues = new HashMap<>();
			newPropertyValues.put(VALIDATION_STATUS, ValidationJobStatus.SCHEDULED.name());
			newPropertyValues.put(PROJECT_KEY, projectKey);
			newPropertyValues.put(TASK_KEY, taskKey);
			newPropertyValues.put(VALIDATION_START_TIMESTAMP, String.valueOf((new Date()).getTime()));
			newPropertyValues.put(VALIDATION_END_TIMESTAMP, null);

			updateValidationCache(branchPath, newPropertyValues);

			return new Status(ValidationJobStatus.SCHEDULED.name());
		} catch (ServiceException | ExecutionException e) {
			throw new BusinessServiceException("Failed to read branch information, validation request not sent.", e);
		}
	}

	private ValidationConfiguration constructValidationConfig(final String branchPath, final Map <String, Object> branchMetadata, String effectiveDate, boolean enableMRCM, String projectKey, String taskKey) {
		ValidationConfiguration validationConfig = new ValidationConfiguration();
        validationConfig.setBranchPath(branchPath);
		validationConfig.setAssertionGroupNames((String) branchMetadata.get(ASSERTION_GROUP_NAMES));
		validationConfig.setPreviousPackage((String) branchMetadata.get(PREVIOUS_PACKAGE));
		validationConfig.setDependencyPackage((String) branchMetadata.get(DEPENDENCY_PACKAGE));
		validationConfig.setPreviousRelease((String) branchMetadata.get(PREVIOUS_RELEASE));

		Set<String> moduleIds = new HashSet<>();
		if (branchMetadata.containsKey(DEFAULT_MODULE_ID)) {
			String defaultModuleId = (String) branchMetadata.get(DEFAULT_MODULE_ID);
			moduleIds.add(defaultModuleId);
			validationConfig.setDefaultModuleId(defaultModuleId);
		}
		if (branchMetadata.containsKey(EXPECTED_EXTENSION_MODULES)) {
			moduleIds.addAll((ArrayList<String>) branchMetadata.get(EXPECTED_EXTENSION_MODULES));
		}
		if (!moduleIds.isEmpty()) {
			validationConfig.setIncludedModuleIds(String.join(",", moduleIds));
		}
		if (branchMetadata.containsKey(MAX_FAILURE_EXPORT)) {
			validationConfig.setFailureExportMax((String) branchMetadata.get(MAX_FAILURE_EXPORT));
		}

		validationConfig.setEnableMRCMValidation(enableMRCM);
		validationConfig.setEnableTraceabilityValidation(!"true".equalsIgnoreCase((String) branchMetadata.get(DISABLE_TRACEABILITY_VALIDATION)));
		validationConfig.setEnableDroolsValidation(branchMetadata.get(ENABLE_DROOLS_VALIDATION) == null || "true".equalsIgnoreCase((String) branchMetadata.get(ENABLE_DROOLS_VALIDATION)));
		String dependencyRelease = (String) branchMetadata.get(DEPENDENCY_RELEASE);
		if (dependencyRelease != null) {
			validationConfig.setReleaseCenter((String) branchMetadata.get(SHORT_NAME));
			validationConfig.setDependencyRelease(dependencyRelease);
		} else {
			validationConfig.setReleaseCenter(INTERNATIONAL);
		}
        validationConfig.setProductName(branchPath.replace("/", "_"));
        validationConfig.setReleaseDate(effectiveDate != null ? effectiveDate : DateUtils.now(DateUtils.YYYYMMDD));
		validationConfig.setProjectKey(projectKey);
		validationConfig.setTaskKey(taskKey);

		logger.info("Validation config created:{}", validationConfig);
		return validationConfig;
	}

	public String getValidationJsonForBranch(String branchPath) throws BusinessServiceException {
		return getValidationJsonIfAvailable(branchPath);
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
			Validation validation = getValidation(path);
			JSONObject jsonObj = new JSONObject();
			if (validation != null) {
				if (validation.getStartTimestamp() != null) {
					jsonObj.put("startTimestamp", validation.getStartTimestamp());
				}
				if (validation.getEndTimestamp() != null) {
					jsonObj.put("endTimestamp", validation.getEndTimestamp());
				}
			}
			if (validation != null && validation.getDailyBuildReportUrl() != null) {
				jsonObj.put("dailyBuildRvfUrl", validation.getDailyBuildReportUrl());
				jsonObj.put("dailyBuildReport", rvfRestTemplate.getForObject(validation.getDailyBuildReportUrl(), String.class));
			} else {
				jsonObj.put("dailyBuildRvfUrl", null);
				jsonObj.put("dailyBuildReport", null);
			}

			if (ValidationJobStatus.COMPLETED.name().equals(validation.getStatus()) && validation.getReportUrl() != null) {
				jsonObj.put("rvfUrl", validation.getReportUrl());

				String report = rvfRestTemplate.getForObject(validation.getReportUrl(), String.class);
				if (StringUtils.hasLength(report) && validation.getContentHeadTimestamp() != null) {
					Branch branch = branchService.getBranch(path);
					if (!validation.getContentHeadTimestamp().equals(branch.getHeadTimestamp())) {
						jsonObj.put("executionStatus", ValidationJobStatus.STALE.name());
					} else {
						jsonObj.put("executionStatus", validation.getStatus());
					}
				} else {
					jsonObj.put("executionStatus", validation.getStatus());
				}
				jsonObj.put("report", report);
				return  jsonObj.toString();
			} else if (validation.getStatus() != null) {
				jsonObj.put("executionStatus", validation.getStatus());
				return  jsonObj.toString();
			} else {
				logger.warn("Ignoring request for validation json for path {} as status {} ", path, validation.getStatus());
				return null;
			}
		} catch (Exception e) {
			throw new BusinessServiceException ("Unable to recover validation json for " + path, e);
		}
	}

	public ImmutableMap<String, Validation> getValidations(Collection<String> paths) throws ExecutionException {
		return validationLoadingCache.getAll(paths);
	}
	
	public Validation getValidation(String path) throws ExecutionException {
		return validationLoadingCache.get(path);
	}

	public Set<String> getTechnicalItems() {
		return this.technicalItems;
	}

	public void insertTechnicalItems(Set<String> newAssertionUUIDs) throws IOException {
		Set<String> combined = Stream.of(this.technicalItems, newAssertionUUIDs)
				.flatMap(Collection::stream)
				.collect(Collectors.toSet());
		if(!this.technicalItems.equals(combined)) {
			writeAndPutFileToS3(combined, techinicalIssueItemsPath);
			this.technicalItems = combined;
		}
	}

	public void deleteTechnicalItem(String assertionUUID) throws IOException {
		if(this.technicalItems.contains(assertionUUID)) {
			this.technicalItems.remove(assertionUUID);
			writeAndPutFileToS3(this.technicalItems, techinicalIssueItemsPath);
		} else {
			throw new ResourceNotFoundException("UUID not found: " + assertionUUID);
		}
	}

	public void insertSemanticTags(Set<String> semanticTags) throws IOException {
		Set<String> existingSemanticTags = loadSemanticTags();
		Set<String> combined = Stream.of(existingSemanticTags, semanticTags)
				.flatMap(Collection::stream)
				.collect(Collectors.toSet());
		if(!existingSemanticTags.equals(combined)) {
			writeAndPutFileToS3(combined, semanticTagItemsPath);
			this.technicalItems = combined;
		}
	}

	public void deleteSemanticTag(String semanticTag) throws IOException {
		Set<String> existingSemanticTags = loadSemanticTags();
		if(existingSemanticTags.contains(semanticTag)) {
			existingSemanticTags.remove(semanticTag);
			writeAndPutFileToS3(existingSemanticTags, semanticTagItemsPath);
		} else {
			throw new ResourceNotFoundException("Semantic tag not found: " + semanticTag);
		}
	}

	private void writeAndPutFileToS3(final Set<String> lines, final String path) throws IOException {
		File modifiedList = Files.createTempFile("temp", ".txt").toFile();
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(modifiedList))) {
			for (String line: lines) {
				writer.write(line);
				writer.newLine();
			}
		}
		try {
			S3ClientImpl s3Client = new S3ClientImpl(new BasicAWSCredentials(accessKey, secretKey));
			s3Client.putObject(new PutObjectRequest(bucket, path, modifiedList)
					.withCannedAcl(CannedAccessControlList.PublicRead));
		} finally {
			FileUtils.forceDelete(modifiedList);
		}
	}

	private Set<String> loadSemanticTags(){
		Set<String> semanticTags = new HashSet<>();
		S3ClientImpl s3Client = new S3ClientImpl(new BasicAWSCredentials(accessKey, secretKey));
		if (s3Client.doesObjectExist(this.bucket, this.semanticTagItemsPath)) {
			S3ObjectInputStream objectContent = s3Client.getObject(bucket, semanticTagItemsPath).getObjectContent();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(objectContent))) {
				String line;
				while ((line = reader.readLine()) != null) {
					semanticTags.add(line);
				}
			} catch (IOException e) {
				logger.error("Failed to load semantic tags from S3", e);
			}
		}
		return semanticTags;
	}

	@Transactional
	private Map<String, Validation> getValidationStatusesWithoutCache(List<String> paths) {
		List<Validation> validations = validationRepository.findAllByBranchPathIn(paths);
		Map<String, Validation> branchToValidationMap = validations.stream().collect(Collectors.toMap(Validation::getBranchPath, Function.identity()));
        for (String path : paths) {
            Validation validation;
            if (!branchToValidationMap.containsKey(path)) {
                validation = new Validation(path);
                validation.setStatus(ValidationJobStatus.NOT_TRIGGERED.name());
                validation = validationRepository.save(validation);
                branchToValidationMap.put(path, validation);
            }
        }

		return branchToValidationMap;
	}

	public void refreshValidationStatusCache() {
		validationLoadingCache.invalidateAll();
		init();
	}

	public void resetBranchValidationStatus(String branchPath) {
		Map<String, String> newPropertyValues = new HashMap();
		newPropertyValues.put(VALIDATION_STATUS, ValidationJobStatus.NOT_TRIGGERED.name());
		updateValidationCache(branchPath, newPropertyValues);
	}
}
