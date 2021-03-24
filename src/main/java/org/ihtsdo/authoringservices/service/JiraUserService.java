package org.ihtsdo.authoringservices.service;

import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.authoringservices.service.jira.ImpersonatingJiraClientFactory;
import org.ihtsdo.authoringservices.service.jira.JiraHelper;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JiraUserService {

    @Autowired
	private ImpersonatingJiraClientFactory jiraClientFactory;

    @Value("${jira.groupName:ihtsdo-sca-author}")
	private String groupName;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public Object getUsers(String expand) throws JiraException {
		// Jira doesn't allow listing users without admin rights so we are using the authoring-services username here rather than the logged in user.
		JiraClient adminJiraClient = jiraClientFactory.getAdminInstance();

		return JiraHelper.findUsersByGroupName(adminJiraClient, expand, groupName);
	}

	public Object searchUsers(String username, String projectKeys, String issueKey, int maxResults, int startAt) throws JiraException {
		// Jira doesn't allow listing users without admin rights so we are using the authoring-services username here rather than the logged in user.
		JiraClient adminJiraClient = jiraClientFactory.getAdminInstance();

		return JiraHelper.searchUsers(adminJiraClient, username, groupName, projectKeys, issueKey, maxResults, startAt);
	}

	public void deleteIssueLink(String issueKey, String linkId) throws JiraException {
		JiraHelper.deleteIssueLink(getJiraClient(), issueKey, linkId);
	}

	private JiraClient getJiraClient() {
		return jiraClientFactory.getImpersonatingInstance(SecurityUtil.getUsername());
	}

}
