package org.ihtsdo.authoringservices.service;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.ihtsdo.authoringservices.domain.*;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.authoringservices.service.factory.ProjectServiceFactory;
import org.ihtsdo.authoringservices.service.factory.TaskServiceFactory;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.PathHelper;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Merge;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.MergeReviewsResults;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.ihtsdo.otf.rest.client.terminologyserver.pojo.Merge.Status.IN_PROGRESS;
import static org.ihtsdo.otf.rest.client.terminologyserver.pojo.Merge.Status.SCHEDULED;
import static org.ihtsdo.otf.rest.client.terminologyserver.pojo.MergeReviewsResults.MergeReviewStatus.CURRENT;

@Service
public class BranchService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String SNOMEDCT = "SNOMEDCT";

    @Autowired
    private SnowstormRestClientFactory snowstormRestClientFactory;

    @Autowired
    private CodeSystemService codeSystemService;

    @Autowired
    private ProjectServiceFactory projectServiceFactory;

    @Autowired
    private TaskServiceFactory taskServiceFactory;

    @Autowired
    private BranchServiceCache branchServiceCache;

    public AuthoringInfoWrapper getBranchAuthoringInfoWrapper(String branchPath) throws BusinessServiceException, RestClientException, ServiceException {
        String[] parts = branchPath.split("/");
        Information result = getBranchInformation(branchPath, parts);

        // Verify that the code system branch path must be valid
        String codeSystemBranch;
        if (result.taskKey != null) {
            codeSystemBranch = PathHelper.getParentPath(PathHelper.getParentPath(branchPath));
        } else if (result.projectKey != null) {
            codeSystemBranch = PathHelper.getParentPath(branchPath);
        } else {
            codeSystemBranch = branchPath;
        }
        Branch branchOrNull = getBranchOrNull(codeSystemBranch);
        if (branchOrNull == null)  throw new BusinessServiceException("Code system branch " + codeSystemBranch + " does not exist.");

        AuthoringCodeSystem codeSystem = getAuthoringCodeSystem(result.codeSystemShortname());
        AuthoringProject project = getAuthoringProject(result.projectKey());
        AuthoringTask task = getAuthoringTask(result.taskKey(), result.projectKey());
        return new AuthoringInfoWrapper(codeSystem, project, task);
    }

    @NotNull
    private static BranchService.Information getBranchInformation(String branchPath, String[] parts) {
        String codeSystemShortname = null;
        String projectKey = null;
        String taskKey = null;
        if (branchPath.startsWith("MAIN/SNOMEDCT-")) {
            return getManagedServiceInformation(parts, codeSystemShortname, projectKey, taskKey);
        } else {
            return getInternationBranchInformation(parts, projectKey, taskKey);
        }
    }

    @NotNull
    private static Information getManagedServiceInformation(String[] parts, String codeSystemShortname, String projectKey, String taskKey) {
        for (int i = 0; i < parts.length; i++) {
            if (i == 1) codeSystemShortname = parts[i];
            if (i == 2) projectKey = parts[i];
            if (i == 3) taskKey = parts[i];
        }
        return new Information(codeSystemShortname, projectKey, taskKey);
    }

    @NotNull
    private static Information getInternationBranchInformation(String[] parts, String projectKey, String taskKey) {
        for (int i = 0; i < parts.length; i++) {
            if (i == 1) projectKey = parts[i];
            if (i == 2) taskKey = parts[i];
        }
        return new Information(SNOMEDCT, projectKey, taskKey);
    }

    private record Information(String codeSystemShortname, String projectKey, String taskKey) {
    }

    private AuthoringTask getAuthoringTask(String taskKey, String projectKey) throws BusinessServiceException {
        AuthoringTask task = null;
        if (taskKey != null) {
            task = taskServiceFactory.getInstanceByKey(taskKey).retrieveTask(projectKey, taskKey, true, true);
        }
        return task;
    }

    private AuthoringProject getAuthoringProject(String projectKey) throws BusinessServiceException {
        AuthoringProject project = null;
        if (projectKey != null) {
            project = projectServiceFactory.getInstanceByKey(projectKey).retrieveProject(projectKey, true);
        }
        return project;
    }

    @Nullable
    private AuthoringCodeSystem getAuthoringCodeSystem(String codeSystemShortname) throws BusinessServiceException, RestClientException {
        AuthoringCodeSystem codeSystem = null;
        if (codeSystemShortname != null) {
            codeSystem = codeSystemService.findOne(codeSystemShortname);
            if (codeSystem != null) {
                codeSystem.setVersions(codeSystemService.getCodeSystemVersions(codeSystemShortname));
            }
        }
        return codeSystem;
    }

    public org.ihtsdo.otf.rest.client.terminologyserver.pojo.Branch getBranch(String branchPath) throws ServiceException {
        try {
            return snowstormRestClientFactory.getClient().getBranch(branchPath);
        } catch (RestClientException e) {
            throw new ServiceException("Failed to fetch branch state for branch " + branchPath, e);
        }
    }

    public Branch getBranchOrNull(String branchPath) throws ServiceException {
        return branchServiceCache.getBranchOrNull(branchPath);
    }

    public String getBranchStateOrNull(String branchPath) throws ServiceException {
        final Branch branchOrNull = getBranchOrNull(branchPath);
        return branchOrNull == null ? null : branchOrNull.getState();
    }

    private void createBranch(String branchPath) throws ServiceException {
        try {
            snowstormRestClientFactory.getClient().createBranch(branchPath);
        } catch (RestClientException e) {
            throw new ServiceException("Failed to create branch " + branchPath, e);
        }
    }

    public void createBranchIfNeeded(String branchPath) throws ServiceException {
        if (getBranchOrNull(branchPath) == null) {
            createBranch(branchPath);
        }
    }

    public String getTaskBranchPathUsingCache(String projectKey, String taskKey) throws BusinessServiceException {
        return PathHelper.getTaskPath(projectServiceFactory.getInstanceByKey(projectKey).getProjectBaseUsingCache(projectKey), projectKey, taskKey);
    }

    public String getProjectBranchPathUsingCache(String projectKey) throws BusinessServiceException {
        return PathHelper.getProjectPath(projectServiceFactory.getInstanceByKey(projectKey).getProjectBaseUsingCache(projectKey), projectKey);
    }

    public String getProjectOrTaskBranchPathUsingCache(String projectKey, String taskKey) throws BusinessServiceException {
        if (!Strings.isNullOrEmpty(projectKey)) {
            final String extensionBase = projectServiceFactory.getInstanceByKey(projectKey).getProjectBaseUsingCache(projectKey);
            if (!Strings.isNullOrEmpty(taskKey)) {
                return PathHelper.getTaskPath(extensionBase, projectKey, taskKey);
            }
            return PathHelper.getProjectPath(extensionBase, projectKey);
        }
        return null;
    }

    public Map<String, Object> getBranchMetadataIncludeInherited(String path) throws ServiceException {
        Map<String, Object> mergedMetadata = null;
        List<String> stackPaths = getBranchPathStack(path);
        for (String stackPath : stackPaths) {
            final Branch branch = getBranchOrNull(stackPath);
            final Map<String, Object> metadata = branch.getMetadata();
            if (mergedMetadata == null) {
                mergedMetadata = metadata;
            } else {
                // merge metadata
                for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                    if (!entry.getKey().equals("lock") || stackPath.equals(path)) { // Only copy lock info from the deepest branch
                        mergedMetadata.put(entry.getKey(), metadata.get(entry.getKey()));
                    }
                }
            }
        }
        return mergedMetadata;
    }

    public Merge mergeBranchSync(String sourcePath, String targetPath, String reviewId) throws BusinessServiceException {
        logger.info("Attempting branch merge from '{}' to '{}'", sourcePath, targetPath);
        SnowstormRestClient client = snowstormRestClientFactory.getClient();
        String mergeId;
        try {
            mergeId = client.startMerge(sourcePath, targetPath, reviewId);
        } catch (RestClientException e) {
            throw new BusinessServiceException("Failed to start merge.", e);
        }

        try {
            Merge merge = waitToMergeBranchComplete(client, mergeId);
            logger.info("Branch merge from '{}' to '{}' end status is {} {}", sourcePath, targetPath, merge.getStatus(), merge.getApiError() == null ? "" : merge.getApiError());
            return merge;

        } catch (RestClientException e) {
            throw new BusinessServiceException("Failed to fetch merge status.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessServiceException("Thread Interrupted!.", e);
        }

    }

    private static Merge waitToMergeBranchComplete(SnowstormRestClient client, String mergeId) throws InterruptedException, RestClientException {
        int sleepSeconds = 4;
        int totalWait = 0;
        int maxTotalWait = 60 * 60;
        Merge merge;
        do {
            Thread.sleep(1000L * sleepSeconds);
            totalWait += sleepSeconds;
            merge = client.getMerge(mergeId);
            if (sleepSeconds < 10) {
                sleepSeconds += 2;
            }
        } while (totalWait < maxTotalWait && (merge.getStatus() == SCHEDULED || merge.getStatus() == IN_PROGRESS));

        return merge;
    }

    public String generateBranchMergeReviews(String sourceBranchPath, String targetBranchPath) throws InterruptedException, RestClientException {
        SnowstormRestClient client = snowstormRestClientFactory.getClient();
        String mergeId = client.createBranchMergeReviews(sourceBranchPath, targetBranchPath);
        MergeReviewsResults mergeReview;
        int sleepSeconds = 4;
        int totalWait = 0;
        int maxTotalWait = 60 * 60;

        do {
            Thread.sleep(1000L * sleepSeconds);
            totalWait += sleepSeconds;
            mergeReview = client.getMergeReviewsResult(mergeId);
            if (sleepSeconds < 10) {
                sleepSeconds += 2;
            }
        } while (totalWait < maxTotalWait && (mergeReview.getStatus() != CURRENT));

        return mergeId;
    }

    private List<String> getBranchPathStack(String path) {
        List<String> paths = new ArrayList<>();
        paths.add(path);
        int index;
        while ((index = path.lastIndexOf("/")) != -1) {
            path = path.substring(0, index);
            paths.add(path);
        }
        return Lists.reverse(paths);
    }

}
