package org.ihtsdo.snowowl.authoring.single.api.service;

import java.net.URISyntaxException;

import org.ihtsdo.snowowl.authoring.single.api.service.jira.ImpersonatingJiraClientFactory;
import org.ihtsdo.snowowl.authoring.single.api.service.jira.JiraHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;

public class ConfigurationService {
	private final ImpersonatingJiraClientFactory jiraClientFactory;	
	private final String  jiraUser;
	private final String groupName;
	private static final String DEFAULT_GROUP_NAME = "ihtsdo-sca-author";
	private Logger logger = LoggerFactory.getLogger(getClass());
	
	public ConfigurationService(ImpersonatingJiraClientFactory jiraClientFactory, String jiraUser, String groupName) throws JiraException {
		this.jiraClientFactory = jiraClientFactory;
		this.jiraUser = jiraUser;
		
		logger.info("Group name: " + groupName);
		if(groupName == null || groupName.isEmpty()) {
			this.groupName = DEFAULT_GROUP_NAME;
		} else {
			this.groupName = groupName;
		}		
	}
	
	public Object gettUsers(String expand) throws JiraException, URISyntaxException {
		final JiraClient jiraClient = jiraClientFactory.getImpersonatingInstance(jiraUser);
		return JiraHelper.findtUsersByGroupName(jiraClient, expand, groupName);
	}

	public Object searchUsers(String username, String projectKeys, String issueKey, int maxResults, int startAt) throws JiraException {
		final JiraClient jiraClient = jiraClientFactory.getImpersonatingInstance(jiraUser);
		return JiraHelper.searchUsers(jiraClient, username, groupName, projectKeys, issueKey, maxResults, startAt);
	}

	public void deleteIssueLink(String issueKey, String linkId) throws JiraException {
		final JiraClient jiraClient = jiraClientFactory.getImpersonatingInstance(jiraUser);
		JiraHelper.deleteIssueLink(jiraClient, issueKey, linkId);
		
	}
}
