package org.ihtsdo.authoringservices.service;

import com.google.common.io.Files;
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
            String exportEffectiveTime = resolveExportEffectiveTime(config);

            // Export RF2 delta
            final Branch branch = snowstormRestClient.getBranch(branchPath);
            exportArchive = snowstormRestClient.export(branchPath, exportEffectiveTime, null, UNPUBLISHED, DELTA);
            config.setContentHeadTimestamp(branch.getHeadTimestamp());
            config.setContentBaseTimestamp(branch.getBaseTimestamp());

            // send delta export directly for RVF validation
            validateByRvfDirectly(exportArchive);
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

    private String resolveExportEffectiveTime(ValidationConfiguration config) throws ParseException {
        String exportEffectiveDate = config.getReleaseDate();
        String mostRecentRelease = null;
        if (config.getDependencyRelease() != null) {
            mostRecentRelease = config.getDependencyRelease();
        } else if (config.getPreviousRelease() != null) {
            mostRecentRelease = config.getPreviousRelease();
        }
        if (mostRecentRelease != null) {
            String[] splits = mostRecentRelease.split("_");
            String dateStr = (splits.length == 2) ? splits[1] : splits[0];
            Calendar calendar = new GregorianCalendar();
            SimpleDateFormat formatter = new SimpleDateFormat(DateUtils.YYYYMMDD);
            if (!formatter.parse(config.getReleaseDate()).after(formatter.parse(dateStr))) {
                calendar.setTime(formatter.parse(dateStr));
                calendar.add(Calendar.DAY_OF_YEAR, 1);
                exportEffectiveDate = formatter.format(calendar.getTime());
                logger.debug("The effective date for termServer exporting is set to {} one day after the most recent release {}", exportEffectiveDate, mostRecentRelease);
            }
        }
        return exportEffectiveDate;
    }

    public void validateByRvfDirectly(File exportArchive) throws ServiceException {
        File tempDir = Files.createTempDir();
        File localZipFile = new File(tempDir, config.getProductName() + "_" + config.getReleaseDate() + ".zip");
        try {
            // prepare files for validation
            prepareExportFilesForValidation(exportArchive, config, localZipFile);

            // call validation API
            runValidationForRF2DeltaExport(localZipFile, config);
        } catch (IOException | ProcessWorkflowException e) {
			throw new ServiceException("Validation failed.", e);
		} finally {
			localZipFile.deleteOnExit();
			tempDir.deleteOnExit();
            if (exportArchive != null) {
                exportArchive.deleteOnExit();
            }
        }
    }

    public void prepareExportFilesForValidation(File exportArchive, ValidationConfiguration config, File localZipFile) throws ProcessWorkflowException, IOException {
        File extractDir = null;
        try {
            extractDir = srsDAO.extractAndConvertExportWithRF2FileNameFormat(exportArchive, config.getReleaseCenter(), config.getReleaseDate());
            ZipFileUtils.zip(extractDir.getAbsolutePath(), localZipFile.getAbsolutePath());
        } finally {
            if (extractDir != null) {
                extractDir.deleteOnExit();
            }
        }
    }

    public void runValidationForRF2DeltaExport(File zipFile, ValidationConfiguration config) throws IOException, ServiceException {
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
        // If RvfDroolsAssertionGroupNames is not empty, enable Drools validation on RVF
        if (config.isEnableDroolsValidation() && StringUtils.isNotEmpty(config.getAssertionGroupNames())) {
            body.add("enableDrools", Boolean.TRUE.toString());
            body.add("droolsRulesGroups", config.getAssertionGroupNames());
        }
        if (StringUtils.isNotBlank(config.getReleaseDate())) {
            body.add("effectiveTime", config.getReleaseDate());
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
