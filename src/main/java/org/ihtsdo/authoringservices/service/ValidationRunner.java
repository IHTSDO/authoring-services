package org.ihtsdo.authoringservices.service;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.ihtsdo.authoringservices.domain.EntityType;
import org.ihtsdo.authoringservices.domain.Notification;
import org.ihtsdo.authoringservices.domain.ValidationConfiguration;
import org.ihtsdo.authoringservices.domain.ValidationJobStatus;
import org.ihtsdo.authoringservices.service.dao.SRSFileDAO;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Branch;
import org.ihtsdo.otf.rest.exception.BadRequestException;
import org.ihtsdo.otf.rest.exception.ProcessWorkflowException;
import org.ihtsdo.otf.utils.DateUtils;
import org.ihtsdo.otf.utils.ZipFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

import static org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient.ExportCategory.UNPUBLISHED;
import static org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient.ExportType.DELTA;

public class ValidationRunner implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String RVF_TS = "RVF_TS";

    private final SnowstormRestClient snowstormRestClient;

    private final ValidationService validationService;

    private final NotificationService notificationService;

    protected SRSFileDAO srsDAO;

    private final RestTemplate restTemplate;

    private final ValidationConfiguration config;

    private final String username;

    private final String authenticationToken;

    private final String scaQueuePrefix;

    public ValidationRunner(ValidationConfiguration validationConfig,
                            SnowstormRestClient snowstormRestClient,
                            SRSFileDAO srsDAO,
                            ValidationService validationService,
                            NotificationService notificationService,
                            String rvfUrl,
                            String scaQueuePrefix,
                            String username,
                            String authenticationToken) {
        this.config = validationConfig;
        this.snowstormRestClient = snowstormRestClient;
        this.srsDAO = srsDAO;
        this.validationService = validationService;
        this.notificationService = notificationService;
        this.username = username;
        this.authenticationToken = authenticationToken;
        this.restTemplate = new RestTemplateBuilder().rootUri(rvfUrl).build();
        this.scaQueuePrefix = scaQueuePrefix;
    }

    @Override
    public void run() {
        File exportArchive = null;
        String branchPath = config.getBranchPath();
        try {

            // check the config is set correctly
            String errorMsg = config.checkMissingParameters();
            if (errorMsg != null) {
                throw new BadRequestException("Validation configuration is not set correctly:" + errorMsg);
            }
            //check and update export effective time
            String effectiveTime = resolveEffectiveTime(config);

            // Export RF2 delta
            final Branch branch = snowstormRestClient.getBranch(branchPath);
            exportArchive = snowstormRestClient.export(branchPath, effectiveTime, null, UNPUBLISHED, DELTA);
            config.setContentHeadTimestamp(branch.getHeadTimestamp());
            config.setContentBaseTimestamp(branch.getBaseTimestamp());

            // send delta export directly for RVF validation
            validateByRvfDirectly(exportArchive, effectiveTime);
        } catch (Exception e) {
            Map<String, String> newPropertyValues = new HashMap<>();
            newPropertyValues.put(ValidationService.VALIDATION_STATUS, ValidationJobStatus.FAILED.name());
            validationService.updateValidationCache(config.getBranchPath(), newPropertyValues);
            logger.error("Validation of {} failed.", branchPath, e);

            // Notify user
            notificationService.queueNotification(
                    username,
                    new Notification(
                            config.getProjectKey(),
                            config.getTaskKey(),
                            EntityType.Validation,
                            ValidationJobStatus.FAILED.name()));
        } finally {
            if (exportArchive != null) {
                exportArchive.deleteOnExit();
            }
        }
    }

    private String resolveEffectiveTime(ValidationConfiguration config) throws ParseException {
        String exportEffectiveDate = config.getReleaseDate();
        Calendar calendar = new GregorianCalendar();
        SimpleDateFormat formatter = new SimpleDateFormat(DateUtils.YYYYMMDD);
        String mostRecentRelease = null;
        if (config.getDependencyRelease() != null) {
            String[] splits = config.getDependencyRelease().split("_");
            mostRecentRelease = (splits.length == 2) ? splits[1] : splits[0];
        }
        if (config.getPreviousRelease() != null) {
            String[] splits = config.getPreviousRelease().split("_");
            String dateStr = (splits.length == 2) ? splits[1] : splits[0];
            if (mostRecentRelease == null || formatter.parse(dateStr).after(formatter.parse(mostRecentRelease))) {
                mostRecentRelease = dateStr;
            }
        }
        if (mostRecentRelease != null && (formatter.parse(config.getReleaseDate()).compareTo(formatter.parse(mostRecentRelease)) <= 0)) {
            calendar.setTime(formatter.parse(mostRecentRelease));
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            exportEffectiveDate = formatter.format(calendar.getTime());
            logger.debug("The effective date for termServer exporting is set to {} one day after the most recent release {}", exportEffectiveDate, mostRecentRelease);
        }
        return exportEffectiveDate;
    }

    public void validateByRvfDirectly(File exportArchive, String effectiveTime) throws ServiceException {
        File tempDir = null;
        File localZipFile = null;
        try {
            tempDir = Files.createTempDirectory("rvf-temp").toFile();
            localZipFile = new File(tempDir, config.getProductName() + "_" + effectiveTime + ".zip");
            // prepare files for validation
            prepareExportFilesForValidation(exportArchive, config, localZipFile, effectiveTime);

            // call validation API
            runValidationForRF2DeltaExport(localZipFile, config, effectiveTime);
        } catch (IOException | ProcessWorkflowException e) {
			throw new ServiceException("Validation failed.", e);
		} finally {
            if (localZipFile != null) {
                FileUtils.deleteQuietly(localZipFile);
            }
            if (tempDir != null) {
                // Better to delete temp files after use rather than deleteOnExit()
                FileUtils.deleteQuietly(tempDir);
            }
        }
    }

    public void prepareExportFilesForValidation(File exportArchive, ValidationConfiguration config, File localZipFile, String effectiveTime) throws ProcessWorkflowException, IOException {
        File extractDir = null;
        try {
            extractDir = srsDAO.extractAndConvertExportWithRF2FileNameFormat(exportArchive, config.getReleaseCenter(), effectiveTime);
            ZipFileUtils.zip(extractDir.getAbsolutePath(), localZipFile.getAbsolutePath());
        } finally {
            if (extractDir != null) {
                // Better to delete temp files after use rather than deleteOnExit()
                FileUtils.deleteDirectory(extractDir);
            }
        }
    }

    public void runValidationForRF2DeltaExport(File zipFile, ValidationConfiguration config, String effectiveTime) throws IOException, ServiceException {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        MultiValueMap<String, String> fileMap = new LinkedMultiValueMap<>();
        ContentDisposition contentDisposition = ContentDisposition
                .builder("form-data")
                .name("file")
                .filename(zipFile.getName())
                .build();
        fileMap.add(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString());
        fileMap.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
        HttpEntity<byte[]> fileEntity = new HttpEntity<>(FileUtils.readFileToByteArray(zipFile), fileMap);

        body.add("file", fileEntity);
        body.add("rf2DeltaOnly", Boolean.TRUE.toString());
        if (config.getPreviousPackage() != null) {
            //use release package for PIP-12
            body.add("previousRelease", config.getPreviousPackage());
        }
        if (config.getDependencyPackage() != null) {
            body.add("dependencyRelease", config.getDependencyPackage());
        }
        body.add("groups", config.getAssertionGroupNames());
        if (config.getAssertionExclusionList() != null) {
            body.add("assertionExclusionList", config.getAssertionExclusionList());
        }
        // If RvfDroolsAssertionGroupNames is not empty, enable Drools validation on RVF
        if (config.isEnableDroolsValidation() && StringUtils.isNotEmpty(config.getAssertionGroupNames())) {
            body.add("enableDrools", Boolean.TRUE.toString());
            body.add("droolsRulesGroups", config.getAssertionGroupNames());
        }
        if (StringUtils.isNotBlank(effectiveTime)) {
            body.add("effectiveTime", effectiveTime);
        }
        if (StringUtils.isNotBlank(config.getDefaultModuleId())) {
            body.add("defaultModuleId", config.getDefaultModuleId());
        }
        if (StringUtils.isNotBlank(config.getIncludedModuleIds())) {
            body.add("includedModules", config.getIncludedModuleIds());
        }
        String runId = Long.toString(System.currentTimeMillis());
        body.add("runId", runId);
        body.add("failureExportMax", config.getFailureExportMax());
        String storageLocation = RVF_TS + "/" + config.getProductName() + "/" + runId;
        body.add("storageLocation", storageLocation);
        body.add("enableMRCMValidation", Boolean.toString(config.isEnableMRCMValidation()));
        body.add("enableTraceabilityValidation", Boolean.toString(config.isEnableTraceabilityValidation()));
        body.add("branchPath", config.getBranchPath());
        if (config.getContentHeadTimestamp() != null) {
            body.add("contentHeadTimestamp", Long.toString(config.getContentHeadTimestamp()));
        }
        if (config.getContentBaseTimestamp() != null) {
            body.add("contentBaseTimestamp", Long.toString(config.getContentBaseTimestamp()));
        }
        body.add("responseQueue", scaQueuePrefix + "." + ValidationService.VALIDATION_RESPONSE_QUEUE);
        body.add("username", this.username);
        body.add("authenticationToken", this.authenticationToken);

        Map<String, String> newPropertyValues = new HashMap<>();
        newPropertyValues.put(ValidationService.RUN_ID, runId);
        newPropertyValues.put(ValidationService.CONTENT_HEAD_TIMESTAMP, String.valueOf(this.config.getContentHeadTimestamp()));
        validationService.updateValidationCache(config.getBranchPath(), newPropertyValues);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

            URI location = restTemplate.postForLocation("/run-post", entity);
			if (location == null) {
				throw new ServiceException("RVF did not return a location header for new validation.");
			}
            logger.info("RVF Report URL: {}", location);

            newPropertyValues = new HashMap<>();
            newPropertyValues.put(ValidationService.REPORT_URL, location.toString());
            validationService.updateValidationCache(config.getBranchPath(), newPropertyValues);
        } catch (RestClientException e) {
			String message = String.format("Failed to validate for branch:%s", this.config.getBranchPath());
            logger.error(message, e);
            throw new ServiceException(message, e);
        }
    }
}
