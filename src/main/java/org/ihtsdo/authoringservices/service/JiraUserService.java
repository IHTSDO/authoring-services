package org.ihtsdo.authoringservices.service;

import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.authoringservices.service.jira.ImpersonatingJiraClientFactory;
import org.ihtsdo.authoringservices.service.jira.JiraHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JiraUserService {

    @Autowired
	@Qualifier("authoringTaskOAuthJiraClient")
	private ImpersonatingJiraClientFactory jiraClientFactory;

    @Value("${jira.groupName:ihtsdo-sca-author}")
	private String groupName;

	public Object getUsers(int offset, int limit) throws JiraException {
		return findUsersByGroupName(groupName, offset, limit);
	}

	public Object findUsersByGroupName(String group, int offset, int limit) throws JiraException {
		// Jira doesn't allow listing users without admin rights so we are using the authoring-services username here rather than the logged in user.
		JiraClient adminJiraClient = jiraClientFactory.getAdminInstance();

		int endOffset = offset + limit;
		final String expand = String.format("users[%s:%s]", offset, endOffset);
		return JiraHelper.findUsersByGroupName(adminJiraClient, expand, group);
	}

	public Object searchUsers(String username, String projectKeys, String issueKey, int maxResults, int startAt) throws JiraException {
		// Jira doesn't allow listing users without admin rights so we are using the authoring-services username here rather than the logged in user.
		JiraClient adminJiraClient = jiraClientFactory.getAdminInstance();

		return JiraHelper.searchUsers(adminJiraClient, username, groupName, projectKeys, issueKey, maxResults, startAt);
	}
}
