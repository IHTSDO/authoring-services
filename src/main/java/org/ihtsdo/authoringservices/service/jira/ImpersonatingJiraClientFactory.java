package org.ihtsdo.authoringservices.service.jira;

import net.rcarz.jiraclient.JiraClient;

public interface ImpersonatingJiraClientFactory {

	JiraClient getImpersonatingInstance(String username);
	JiraClient getAdminInstance();

}
