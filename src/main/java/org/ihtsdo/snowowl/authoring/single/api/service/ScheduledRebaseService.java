package org.ihtsdo.snowowl.authoring.single.api.service;


import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.ims.IMSRestClient;
import org.ihtsdo.otf.rest.client.snowowl.PathHelper;
import org.ihtsdo.otf.rest.client.snowowl.pojo.ApiError;
import org.ihtsdo.otf.rest.client.snowowl.pojo.Merge;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringProject;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.BranchState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;

import net.rcarz.jiraclient.JiraException;

@Component
public class ScheduledRebaseService {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Value("${jira.username}")
	private String username;

	@Value("${jira.password}")
	private String password;

	@Value("${ims.url}")
	private String imsUrl;

	@Autowired
	private TaskService taskService;
	
	@Autowired
	private BranchService branchService;
	
	private boolean cronJobRunning = false;

	@Scheduled(cron = "${scheduled.rebase.project.cron}")
	@SuppressWarnings("rawtypes")
	public void rebaseProjects()
			throws JiraException, BusinessServiceException, URISyntaxException, IOException, RestClientException, InterruptedException {
		if (cronJobRunning) {
			return;
		} else {
			cronJobRunning = true;
		}
		
		setSecurityContext();
		
		logger.info("Starting scheduled rebase for all configured projects.");

		List<AuthoringProject> projects = taskService.listProjects();
		projects = projects.stream().filter(project -> !project.isProjectScheduledRebaseDisabled()).collect(Collectors.toList());
		for (AuthoringProject project : projects) {
			logger.info("Performing scheduled rebase of project " + project.getKey() + ".");
			if (BranchState.UP_TO_DATE.name().equals(project.getBranchState()) 
			  ||BranchState.FORWARD.name().equals(project.getBranchState())) {
				logger.info("No rebase needed for project  " + project.getKey() + " with branch state " + project.getBranchState() + ".");					
			} else {
				try {
					String projectBranchPath = taskService.getProjectBranchPathUsingCache(project.getKey());
					
					Set mergeReviewResult = branchService.generateBranchMergeReviews(PathHelper.getParentPath(projectBranchPath), projectBranchPath);
					
					// Check conflict of merge review
					if (mergeReviewResult.isEmpty()) {
						Merge merge = branchService.mergeBranchSync(PathHelper.getParentPath(projectBranchPath), projectBranchPath, null);
						if (merge.getStatus() == Merge.Status.COMPLETED) {
							logger.info("Rebase of project " + project.getKey() + " successful.");
						} else if (merge.getStatus().equals(Merge.Status.CONFLICTS)) {
							Map<String, Object> additionalInfo =  merge.getApiError().getAdditionalInfo();
							List conflicts = (List) additionalInfo.get("conflicts");
							logger.info(conflicts.size() + " conflicts found for project " + project.getKey() + " , skipping rebase.");
						} else {
							ApiError apiError = merge.getApiError();
							String message = apiError != null ? apiError.getMessage() : null;
							logger.info("Rebase of project " + project.getKey() + " failed. Error message: " + message);
						}
					} else {
						logger.info(mergeReviewResult.size() + " conflicts found for project " + project.getKey() + " , skipping rebase.");
					}
					
				} catch (BusinessServiceException e) {
					logger.info("Rebase of project " + project.getKey() + " failed. Error message: " + e.getMessage());
				}
			}
		}
		logger.info("Scheduled rebase complete.");
		cronJobRunning = false;
	}

	private void setSecurityContext() throws URISyntaxException, IOException {
		IMSRestClient imsClient = new IMSRestClient(imsUrl);
		String token = imsClient.login(username, password);
		PreAuthenticatedAuthenticationToken decoratedAuthentication = new PreAuthenticatedAuthenticationToken(username,token);
		SecurityContextHolder.getContext().setAuthentication(decoratedAuthentication);
	}

}
