package org.ihtsdo.authoringservices.service.jira;

import com.google.gson.Gson;
import net.rcarz.jiraclient.*;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.ihtsdo.authoringservices.domain.CreateProjectRequest;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

public class JiraHelper {

    private static final Logger logger = LoggerFactory.getLogger(JiraHelper.class);

    private static final String JIRA_BASE_URI = "rest/api/latest/";
    private static final String JIRA_PROJECT_BASE_URI = JIRA_BASE_URI + "project/";

    public static final String JSON_MALFORMED_MSG = "JSON payload is malformed";

    public static String toStringOrNull(Object jsonProperty) {
        if (jsonProperty != null) {
            if (jsonProperty instanceof String string) {
                return string;
            } else if (jsonProperty instanceof Map) {
                return (String) ((Map) jsonProperty).get("value");
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
            final JSONArray fields = getFields(client);
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

    public static JSONArray getFields(JiraClient client) throws URISyntaxException, RestException, IOException {
        final RestClient restClient = client.getRestClient();
        final URI uri = restClient.buildURI(JIRA_BASE_URI + "field");
        return (JSONArray) restClient.get(uri);
    }

    public static Object findUsersByGroupName(JiraClient client, String expand, String groupName) throws JiraException {
        Map<String, String> params = new HashMap<>();
        params.put("expand", expand);
        params.put("groupname", groupName);
        final RestClient restClient = client.getRestClient();
        try {
            URI uri = restClient.buildURI(JIRA_BASE_URI + "group", params);
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
            URI uri = restClient.buildURI(JIRA_BASE_URI + "user/assignable/search", params);
            return restClient.get(uri);
        } catch (IOException | URISyntaxException | RestException e) {
            throw new JiraException("Failed to lookup sca users", e);
        }
    }

    public static void deleteIssueLink(JiraClient client, String issueKey, String linkId) throws JiraException {
        Issue issue = client.getIssue(issueKey);
        List<IssueLink> issueLinks = issue.getIssueLinks();

        for (IssueLink issueLink : issueLinks) {
            if (issueLink.getOutwardIssue() != null
                    && linkId.trim().equalsIgnoreCase(issueLink.getOutwardIssue().getKey())) {
                issueLink.delete();
                break;
            }
        }
    }

    public static boolean deleteIssue(JiraClient client, String issueKey) throws JiraException {
        Issue issue = client.getIssue(issueKey);
        return issue.delete(true);
    }

    public static Project createProject(JiraClient client, CreateProjectRequest request, String projectTemplateId) throws JiraException {
        JSONObject createMetadata = new JSONObject();
        createMetadata.put("key", request.key());
        createMetadata.put("name", request.name());
        createMetadata.put("lead", StringUtils.hasLength(request.lead()) ? request.lead() : SecurityUtil.getUsername());

        return client.createProject(createMetadata, projectTemplateId);
    }

    public static Project updateProject(JiraClient client, String projectKey, JSONObject request) throws JiraException {
        final RestClient restClient = client.getRestClient();
        JSON result;
        try {
            URI uri = restClient.buildURI( JIRA_PROJECT_BASE_URI + projectKey);
            result = restClient.put(uri, request);
        } catch (IOException | URISyntaxException | RestException e) {
            throw new JiraException("Failed to update project " + projectKey, e);
        }

        if (!(result instanceof JSONObject))
            throw new JiraException(JSON_MALFORMED_MSG);

        Gson gson = new Gson();
        return gson.fromJson(result.toString(), Project.class);
    }

    public static void deleteProject(JiraClient jiraClient, String key) throws JiraException {
        final RestClient restClient = jiraClient.getRestClient();
        try {
            URI uri = restClient.buildURI(JIRA_PROJECT_BASE_URI + key);
            restClient.delete(uri);
        } catch (IOException | URISyntaxException | RestException e) {
            throw new JiraException("Failed to delete project " + key, e);
        }
    }

    public static void addActorUsersToProject(JiraClient jiraClient, String projectKey, String roleId, Set<String> groups)
            throws JiraException {
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("group", groups);
            String path = JIRA_PROJECT_BASE_URI + projectKey + "/role/" + roleId;
            jiraClient.getRestClient().post(path, requestBody);
        } catch (Exception ex) {
            throw new JiraException("Failed to add actor users to project " + projectKey, ex);
        }
    }

    public static Set<String> getActorUsersFromProject(JiraClient jiraClient, String projectKey, String roleId)
            throws JiraException {
        JSON result;
        try {
            String path = JIRA_PROJECT_BASE_URI + projectKey + "/role/" + roleId;
            result = jiraClient.getRestClient().get(path);
        } catch (Exception ex) {
            throw new JiraException("Failed to get actor users from project " + projectKey, ex);
        }

        if (!(result instanceof JSONObject))
            throw new JiraException(JSON_MALFORMED_MSG);

        JSONArray users = ((JSONObject) result).getJSONArray("actors");
        Set<String> results = new HashSet<>();
        for (int i = 0; i < users.size(); i++) {
            results.add(users.getJSONObject(i).getString("name"));
        }

        return results;
    }
}
