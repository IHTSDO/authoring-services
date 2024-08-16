package org.ihtsdo.authoringservices.service;

import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.authoringservices.domain.AuthoringProject;
import org.ihtsdo.authoringservices.service.jira.ImpersonatingJiraClientFactory;
import org.ihtsdo.authoringservices.service.jira.JiraHelper;
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

    @Autowired
    @Qualifier("authoringTaskOAuthJiraClient")
    private ImpersonatingJiraClientFactory jiraClientFactory;

    @PreAuthorize("hasPermission('ADMIN', 'global') || hasPermission('ADMIN', #project.codeSystem.branchPath)")
    public boolean deleteIssue(AuthoringProject project, String issueKey) throws JiraException {
        logger.info("Deleting issue with key {} on project {}", issueKey, project.getKey());
        JiraClient adminJiraClient = jiraClientFactory.getAdminInstance();
        return JiraHelper.deleteIssue(adminJiraClient, issueKey);
    }

    @PreAuthorize("hasPermission('ADMIN', 'global') || hasPermission('ADMIN', #project.codeSystem.branchPath)")
    public void deleteIssues(AuthoringProject project, Set<String> issueKeys) throws JiraException {
        logger.info("Deleting issues [{}] on project {}", issueKeys, project.getKey());
        JiraClient adminJiraClient = jiraClientFactory.getAdminInstance();
        for (String issueKey : issueKeys) {
            JiraHelper.deleteIssue(adminJiraClient, issueKey);
        }
    }

}
