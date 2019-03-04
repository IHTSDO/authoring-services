package org.ihtsdo.snowowl.authoring.single.api.service;

import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.snowowl.authoring.single.api.service.jira.ImpersonatingJiraClientFactory;
import org.ihtsdo.snowowl.authoring.single.api.service.jira.JiraHelper;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

public class JiraUserService {

	private final ImpersonatingJiraClientFactory jiraClientFactory;	
	private final String groupName;
	private static final String DEFAULT_GROUP_NAME = "ihtsdo-sca-author";
	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Value("${jira.username}")
	private String adminJiraUsername;

	public JiraUserService(ImpersonatingJiraClientFactory jiraClientFactory, String groupName) {
		this.jiraClientFactory = jiraClientFactory;

		logger.info("Group name: " + groupName);
		if(groupName == null || groupName.isEmpty()) {
			this.groupName = DEFAULT_GROUP_NAME;
		} else {
			this.groupName = groupName;
		}		
	}
	
	public Object getUsers(String expand) throws JiraException {
		// Jira doesn't allow listing users without admin rights so we are using the authoring-services username here rather than the logged in user.
		JiraClient adminJiraClient = getAdminJiraClient();

		return JiraHelper.findUsersByGroupName(adminJiraClient, expand, groupName);
	}

	public Object searchUsers(String username, String projectKeys, String issueKey, int maxResults, int startAt) throws JiraException {
		// Jira doesn't allow listing users without admin rights so we are using the authoring-services username here rather than the logged in user.
		JiraClient adminJiraClient = getAdminJiraClient();

		return JiraHelper.searchUsers(adminJiraClient, username, groupName, projectKeys, issueKey, maxResults, startAt);
	}

	public void deleteIssueLink(String issueKey, String linkId) throws JiraException {
		JiraHelper.deleteIssueLink(getJiraClient(), issueKey, linkId);
	}

	private JiraClient getJiraClient() {
		return jiraClientFactory.getImpersonatingInstance(SecurityUtil.getUsername());
	}

	// Use with caution
	private JiraClient getAdminJiraClient() {
		return jiraClientFactory.getImpersonatingInstance(adminJiraUsername);
	}
}
