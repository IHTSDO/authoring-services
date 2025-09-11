package org.ihtsdo.authoringservices.service;


import org.ihtsdo.authoringservices.domain.AuthoringProject;
import org.ihtsdo.authoringservices.domain.BranchState;
import org.ihtsdo.authoringservices.service.factory.ProjectServiceFactory;
import org.ihtsdo.authoringservices.service.util.ProjectFilterUtil;
import org.ihtsdo.otf.rest.client.ims.IMSRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.PathHelper;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ApiError;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Merge;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ScheduledRebaseService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Value("${auto.rebase.username}")
    private String username;

    @Value("${auto.rebase.password}")
    private String password;

    @Value("${ims.url}")
    private String imsUrl;

    @Autowired
    private ProjectServiceFactory projectServiceFactory;

    @Autowired
    private BranchService branchService;

    @Autowired
    private SnowstormRestClientFactory snowstormRestClientFactory;

    private boolean cronJobRunning = false;

    @Scheduled(cron = "${scheduled.rebase.project.cron}")
    @SuppressWarnings("rawtypes")
    public void rebaseProjects() throws BusinessServiceException {
        if (cronJobRunning) {
            logger.info("Scheduled rebase already running. Ignoring this trigger.");
            return;
        } else {
            cronJobRunning = true;
        }

        try {
            loginToIMSAndSetSecurityContext();
            logger.info("Starting scheduled rebase for all configured projects.");
            List<AuthoringProject> projects = new ArrayList<>(projectServiceFactory.getInstance(true).listProjects(false, null, null));
            List<AuthoringProject> jiraProjects = projectServiceFactory.getInstance(false).listProjects(false, null, null);
            ProjectFilterUtil.joinJiraProjectsIfNotExists(jiraProjects, projects);

            projects = projects.stream().filter(project -> !Boolean.TRUE.equals(project.isProjectScheduledRebaseDisabled())
                            && !Boolean.TRUE.equals(project.isProjectRebaseDisabled())
                            && !Boolean.TRUE.equals(project.isProjectLocked()))
                    .toList();
            for (AuthoringProject project : projects) {
                logger.info("Performing scheduled rebase of project {}.", project.getKey());
                if (project.getBranchState() == null || BranchState.UP_TO_DATE.name().equals(project.getBranchState())
                        || BranchState.FORWARD.name().equals(project.getBranchState())) {
                    logger.info("No rebase needed for project  {} with branch state {}.", project.getKey(), project.getBranchState());
                    continue;
                }
                rebaseProject(project);
            }

            logger.info("Scheduled rebase complete.");
        } catch (IOException | URISyntaxException e) {
            throw new BusinessServiceException("Error while rebasing projects", e);
        } finally {
            cronJobRunning = false;
            SecurityContextHolder.getContext().setAuthentication(null);
        }
    }

    private void rebaseProject(AuthoringProject project) {
        try {
            String projectBranchPath = branchService.getProjectBranchPathUsingCache(project.getKey());
            String mergeId = branchService.generateBranchMergeReviews(PathHelper.getParentPath(projectBranchPath), projectBranchPath);
            SnowstormRestClient client = snowstormRestClientFactory.getClient();
            Set mergeReviewResult = client.getMergeReviewsDetails(mergeId);
            // Check conflict of merge review
            if (mergeReviewResult.isEmpty()) {
                Merge merge = branchService.mergeBranchSync(PathHelper.getParentPath(projectBranchPath), projectBranchPath, mergeId);
                if (merge.getStatus() == Merge.Status.COMPLETED) {
                    logger.info("Rebase of project {} successful.", project.getKey());
                } else if (merge.getStatus().equals(Merge.Status.CONFLICTS)) {
                    Map<String, Object> additionalInfo = merge.getApiError().getAdditionalInfo();
                    List conflicts = (List) additionalInfo.get("conflicts");
                    logger.info("{} conflicts found for project {}, skipping rebase.", conflicts.size(), project.getKey());
                } else {
                    ApiError apiError = merge.getApiError();
                    String message = apiError != null ? apiError.getMessage() : null;
                    logger.info("Rebase of project {} failed. Error message: {}", project.getKey(), message);
                }
            } else {
                logger.info("{} conflicts found for project {}, skipping rebase.", mergeReviewResult.size(), project.getKey());
            }

        } catch (Exception e) {
            logger.error("Rebase of project " + project.getKey() + " failed. Error message: " + e.getMessage(), e);
        }
    }

    @Async
    public void rebaseProjectsManualTrigger() throws BusinessServiceException {
        logger.info("Manual trigger used for Scheduled project rebase.");
        rebaseProjects();
    }

    private void loginToIMSAndSetSecurityContext() throws URISyntaxException, IOException {
        IMSRestClient imsClient = new IMSRestClient(imsUrl);
        String token = imsClient.loginForceNewSession(username, password);
        PreAuthenticatedAuthenticationToken decoratedAuthentication = new PreAuthenticatedAuthenticationToken(username, token);
        SecurityContextHolder.getContext().setAuthentication(decoratedAuthentication);
    }

}
