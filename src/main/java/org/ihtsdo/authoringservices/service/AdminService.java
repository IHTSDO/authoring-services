package org.ihtsdo.authoringservices.service;

import net.rcarz.jiraclient.Field;
import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.Project;
import org.ihtsdo.authoringservices.domain.AuthoringCodeSystem;
import org.ihtsdo.authoringservices.domain.AuthoringProject;
import org.ihtsdo.authoringservices.domain.CreateProjectRequest;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.authoringservices.service.jira.ImpersonatingJiraClientFactory;
import org.ihtsdo.authoringservices.service.jira.JiraHelper;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AdminService {

    private final Logger logger = LoggerFactory.getLogger(AdminService.class);

    private static final String SLASH = "/";
    private static final String AUTHORING_PROJECT_TYPE = "SCA Authoring Project";
    private static final String SCA_PROJECT_CATEGORY = "SCA";
    private static final String ISSUE_TYPE_SCHEME = "SCA Issue Type Scheme";


    @Autowired
    @Qualifier("authoringTaskOAuthJiraClient")
    private ImpersonatingJiraClientFactory jiraClientFactory;

    @Autowired
    private TaskService taskService;

    @Autowired
    private BranchService branchService;

    @PreAuthorize("hasPermission('ADMIN', 'global') || hasPermission('ADMIN', #project.codeSystem.branchPath)")
    public void deleteIssue(AuthoringProject project, String issueKey) throws JiraException {
        logger.info("Deleting issue with key {} on project {}", issueKey, project.getKey());
        JiraClient adminJiraClient = jiraClientFactory.getAdminInstance();
        JiraHelper.deleteIssue(adminJiraClient, issueKey);
    }

    @PreAuthorize("hasPermission('ADMIN', 'global') || hasPermission('ADMIN', #project.codeSystem.branchPath)")
    public void deleteIssues(AuthoringProject project, Set<String> issueKeys) throws JiraException {
        logger.info("Deleting issues [{}] on project {}", issueKeys, project.getKey());
        JiraClient adminJiraClient = jiraClientFactory.getAdminInstance();
        for (String issueKey : issueKeys) {
            JiraHelper.deleteIssue(adminJiraClient, issueKey);
        }
    }

    @PreAuthorize("hasPermission('ADMIN', 'global') || hasPermission('ADMIN', #codeSystem.branchPath)")
    public Project createProject(AuthoringCodeSystem codeSystem, CreateProjectRequest request) throws BusinessServiceException, ServiceException, JiraException {
        logger.info("Creating new project {} on code system {}", request.key(), codeSystem.getShortName());
        JiraClient adminJiraClient = jiraClientFactory.getAdminInstance();
        Project project = JiraHelper.createProject(adminJiraClient, request, JiraHelper.getCategoryIdByName(adminJiraClient, SCA_PROJECT_CATEGORY));

        String issueTypeSchemeId = JiraHelper.getIssueTypeSchemeIdByName(adminJiraClient, ISSUE_TYPE_SCHEME);
        if (issueTypeSchemeId != null) {
            adminJiraClient.associateIssueTypeSchemeToProject(project.getId(), issueTypeSchemeId);
            createFirstIssueForProject(adminJiraClient, request);
        }

        String projectBranchPath = codeSystem.getBranchPath() + SLASH + request.key();
        branchService.createProjectBranchIfNeeded(projectBranchPath);

        return project;
    }

    private void createFirstIssueForProject(JiraClient jiraClient, CreateProjectRequest request) throws BusinessServiceException {
        try {
            jiraClient.createIssue(request.key(), AUTHORING_PROJECT_TYPE)
                    .field(Field.SUMMARY, request.name())
                    .field(Field.DESCRIPTION, request.description())
                    .execute();
        } catch (JiraException e) {
            throw new BusinessServiceException("Failed to create Jira task", e);
        }
    }
}
