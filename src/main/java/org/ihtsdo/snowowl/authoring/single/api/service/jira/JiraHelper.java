package org.ihtsdo.snowowl.authoring.single.api.service.jira;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.rcarz.jiraclient.Issue;
import net.rcarz.jiraclient.IssueLink;
import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.RestClient;
import net.rcarz.jiraclient.RestException;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class JiraHelper {

	private static Logger logger = LoggerFactory.getLogger(JiraHelper.class);

	public static String toStringOrNull(Object jsonProperty) {
		if (jsonProperty != null) {
			if (jsonProperty instanceof String) {
				return (String) jsonProperty;
			} else if (jsonProperty instanceof Map) {
				return (String) ((Map)jsonProperty).get("value");
			} else if (jsonProperty instanceof net.sf.json.JSONNull) {
				return null;
			} else {
				logger.info("Unrecognised Jira Field type {}, {}", jsonProperty.getClass(), jsonProperty);
			}
		}
		return null;
	}

	public static String fieldIdLookup(String fieldName, JiraClient client, Set<String> customFieldsSet) throws JiraException {
		try {
			String fieldId = null;
			final RestClient restClient = client.getRestClient();
			final URI uri = restClient.buildURI("rest/api/latest/field");
			final JSONArray fields = (JSONArray) restClient.get(uri);
			for (int i = 0; i < fields.size(); i++) {
				final JSONObject jsonObject = fields.getJSONObject(i);
				if (fieldName.equals(jsonObject.getString("name"))) {
					fieldId = jsonObject.getString("id");
				}
			}
			if (customFieldsSet != null && fieldId != null) {
				customFieldsSet.add(fieldId);
			}
			return fieldId;
		} catch (IOException | URISyntaxException | RestException e) {
			throw new JiraException("Failed to lookup field ID", e);
		}
	}

	public static Object findUsersByGroupName(JiraClient client, String expand, String groupName) throws JiraException {
		Map<String, String> params = new HashMap<>();
		params.put("expand", expand);
		params.put("groupname", groupName);
		final RestClient restClient = client.getRestClient();
		try {
			URI uri = restClient.buildURI("rest/api/latest/group", params);
			return restClient.get(uri);
		} catch (IOException | URISyntaxException | RestException e) {
			throw new JiraException("Failed to lookup sca users", e);
		}
	}

	public static Object searchUsers(JiraClient client, String username, String groupName, String projectKeys, String issueKey, int maxResults,
			int startAt) throws JiraException {
		Map<String, String> params = new HashMap<>();
		params.put("username", username);
		params.put("groupname", groupName);
		params.put("projectKeys", projectKeys);
		params.put("issueKey", issueKey);
		params.put("startAt", String.valueOf(startAt));
		params.put("maxResults", String.valueOf(maxResults));
		
		final RestClient restClient = client.getRestClient();
		try {
			URI uri = restClient.buildURI("rest/api/latest/user/assignable/search", params);
			Object response = restClient.get(uri);
			return response;
		} catch (IOException | URISyntaxException | RestException e) {
			throw new JiraException("Failed to lookup sca users", e);
		}
	}

	public static void deleteIssueLink(JiraClient client, String issueKey, String linkId) throws JiraException {
		Issue issue = client.getIssue(issueKey);
		List<IssueLink> issueLinks = issue.getIssueLinks();
		
		for (IssueLink issueLink : issueLinks) {
            if(issueLink.getOutwardIssue() != null 
            	&& linkId.trim().equalsIgnoreCase(issueLink.getOutwardIssue().getKey())) {
               issueLink.delete();
               break;
            }
        }
	}
	
}
