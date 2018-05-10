package org.ihtsdo.snowowl.authoring.single.api.service;

import java.net.URISyntaxException;

import org.ihtsdo.snowowl.authoring.single.api.service.jira.ImpersonatingJiraClientFactory;
import org.ihtsdo.snowowl.authoring.single.api.service.jira.JiraHelper;

import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;

public class ConfigurationService {
	private final ImpersonatingJiraClientFactory jiraClientFactory;	
	private final String  jiraUser;

	public ConfigurationService(ImpersonatingJiraClientFactory jiraClientFactory, String jiraUser) throws JiraException {
		this.jiraClientFactory = jiraClientFactory;
		this.jiraUser = jiraUser;
	}
	
	public Object findtUsersByGroupName(String expand, String groupName) throws JiraException, URISyntaxException {
		final JiraClient jiraClient = jiraClientFactory.getImpersonatingInstance(jiraUser);
		return JiraHelper.findtUsersByGroupName(jiraClient, expand, groupName);
	}

	public Object searchUsers(String username, String groupName, String projectKeys, String issueKey, int maxResults, int startAt) throws JiraException {
		final JiraClient jiraClient = jiraClientFactory.getImpersonatingInstance(jiraUser);
		return JiraHelper.searchUsers(jiraClient, username, groupName, projectKeys, issueKey, maxResults, startAt);
	}
}
