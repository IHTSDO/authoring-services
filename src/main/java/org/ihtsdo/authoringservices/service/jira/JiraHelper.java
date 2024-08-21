package org.ihtsdo.authoringservices.service.jira;

import com.google.gson.Gson;
import net.rcarz.jiraclient.*;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.ihtsdo.authoringservices.domain.CreateProjectRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

public class JiraHelper {

    private static Logger logger = LoggerFactory.getLogger(JiraHelper.class);

    public static final String JSON_MALFORMED_MSG = "JSON payload is malformed";

    public static String toStringOrNull(Object jsonProperty) {
        if (jsonProperty != null) {
            if (jsonProperty instanceof String) {
                return (String) jsonProperty;
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

    public static Project createProject(JiraClient client, CreateProjectRequest request, String categoryId) throws JiraException {
        JSONObject createMetadata = new JSONObject();
        createMetadata.put("key", request.key());
        createMetadata.put("name", request.name());
        createMetadata.put("description", request.description());
        createMetadata.put("lead", request.lead());
        createMetadata.put("projectTypeKey", "software");

        if (categoryId != null) {
            createMetadata.put("categoryId", categoryId);
        }
        return client.createProject(createMetadata);
    }

    public static String getCategoryIdByName(JiraClient client, String categoryName) throws JiraException {
        final RestClient restClient = client.getRestClient();
        try {
            URI uri = restClient.buildURI("rest/api/latest/projectCategory");
            Object response = restClient.get(uri);
            if (response != null) {
                Gson gson = new Gson();
                ProjectCategory[] categories = gson.fromJson(response.toString(), ProjectCategory[].class);
                ProjectCategory category = Arrays.stream(categories).filter(cat -> cat.getName().equals(categoryName)).findFirst().orElse(null);
                return category != null ? category.getId() : null;
            }
            return null;
        } catch (IOException | URISyntaxException | RestException e) {
            throw new JiraException("Failed to lookup project categories", e);
        }
    }

    public static String getIssueTypeSchemeIdByName(JiraClient client, String issueTypeSchemeName) throws JiraException {
        final RestClient restClient = client.getRestClient();
        JSON result;
        try {
            URI uri = restClient.buildURI("rest/api/latest/issuetypescheme");
            result = restClient.get(uri);
        } catch (IOException | URISyntaxException | RestException e) {
            throw new JiraException("Failed to get all issue type schemes", e);
        }
        if (!(result instanceof JSONObject))
            throw new JiraException(JSON_MALFORMED_MSG);

        Gson gson = new Gson();
        IssueTypeScheme[] issueTypeSchemes = gson.fromJson(((JSONObject) result).getJSONArray("schemes").toString(), IssueTypeScheme[].class);
        IssueTypeScheme foundIssueTypeScheme = Arrays.stream(issueTypeSchemes).filter(issueTypeScheme -> issueTypeScheme.getName().equals(issueTypeSchemeName)).findFirst().orElse(null);
        return foundIssueTypeScheme != null ? foundIssueTypeScheme.getId() : null;
    }

}
