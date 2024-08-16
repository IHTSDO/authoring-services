package org.ihtsdo.authoringservices.service;

import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.authoringservices.service.jira.ImpersonatingJiraClientFactory;
import org.ihtsdo.authoringservices.service.jira.JiraHelper;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.CodeSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class AdminService {

    private final Logger logger = LoggerFactory.getLogger(AdminService.class);

    @Autowired
    @Qualifier("authoringTaskOAuthJiraClient")
    private ImpersonatingJiraClientFactory jiraClientFactory;

    @PreAuthorize("hasPermission('ADMIN', 'global') || hasPermission('ADMIN', #codeSystem.branchPath)")
    public boolean deleteIssue(CodeSystem codeSystem, String issueKey) throws JiraException {
        logger.info("Deleting issue with key {} on code system {}", issueKey, codeSystem.getShortName());
        JiraClient adminJiraClient = jiraClientFactory.getAdminInstance();
        return JiraHelper.deleteIssue(adminJiraClient, issueKey);
    }

}
