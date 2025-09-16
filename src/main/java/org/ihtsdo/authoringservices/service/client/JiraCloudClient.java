package org.ihtsdo.authoringservices.service.client;

import net.sf.json.JSONObject;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Simple Jira Cloud REST client for basic operations.
 */
public class JiraCloudClient {
    private final String baseUrl;
    private final String authHeader;

    /**
     * @param baseUrl  Jira Cloud instance URL
     * @param email    Jira user email
     * @param apiToken Jira API token
     */
    public JiraCloudClient(String baseUrl, String email, String apiToken) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        String auth = email + ":" + apiToken;
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Get a Jira issue by key.
     */
    public JSONObject getIssue(String issueKey) throws IOException {
        String url = baseUrl + "rest/api/latest/issue/" + issueKey;
        HttpGet request = new HttpGet(url);
        request.setHeader(HttpHeaders.AUTHORIZATION, authHeader);
        request.setHeader(HttpHeaders.ACCEPT, "application/json");
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(request)) {
            String json = EntityUtils.toString(response.getEntity());
            return JSONObject.fromObject(json);
        }
    }

    /**
     * Create a Jira issue (fields must follow Jira Cloud API format).
     */
    public JSONObject createIssue(String projectKey, String summary, String description, String issueType) throws IOException {
        String url = baseUrl + "rest/api/latest/issue";
        HttpPost request = new HttpPost(url);
        request.setHeader(HttpHeaders.AUTHORIZATION, authHeader);
        request.setHeader(HttpHeaders.ACCEPT, "application/json");
        request.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");

        // Construct JSON payload
        JSONObject issueFields = new JSONObject();
        issueFields.put("project", new JSONObject().put("key", projectKey));
        issueFields.put("summary", summary);
        issueFields.put("description", description);
        issueFields.put("issuetype", new JSONObject().put("name", issueType));

        JSONObject payload = new JSONObject();
        payload.put("fields", issueFields);
        request.setEntity(new StringEntity(payload.toString(), StandardCharsets.UTF_8));
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(request)) {
            String json = EntityUtils.toString(response.getEntity());
            return JSONObject.fromObject(json);
        }
    }


    /**
     * Update a Jira issue by key. Fields must follow Jira Cloud API format.
     *
     * @param issueKey Jira issue key (e.g., "PROJ-123")
     * @param fields   Fields to update (as per Jira Cloud API)
     * @return API response as JsonObject
     */
    public JSONObject updateIssue(String issueKey, JSONObject fields) throws IOException {
        String url = baseUrl + "rest/api/latest/issue/" + issueKey;
        org.apache.http.client.methods.HttpPut request = new org.apache.http.client.methods.HttpPut(url);
        request.setHeader(HttpHeaders.AUTHORIZATION, authHeader);
        request.setHeader(HttpHeaders.ACCEPT, "application/json");
        request.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        JSONObject payload = new JSONObject();
        payload.put("fields", fields);
        request.setEntity(new StringEntity(payload.toString(), StandardCharsets.UTF_8));
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(request)) {
            String json = EntityUtils.toString(response.getEntity());
            // Jira Cloud returns 204 No Content for successful update, so handle empty response
            if (json == null || json.isEmpty()) {
                JSONObject result = new JSONObject();
                result.put("status", response.getStatusLine().getStatusCode());
                result.put("message", "No Content");
                return result;
            }
            return JSONObject.fromObject(json);
        }
    }

    /**
     * Add an attachment to a Jira issue.
     *
     * @param issueKey  Jira issue key (e.g., "PROJ-123")
     * @param fileName  Name of the file to attach
     * @param fileBytes File content as byte array
     */
    public void addAttachment(String issueKey, String fileName, byte[] fileBytes) throws IOException {
        String url = baseUrl + "rest/api/latest/issue/" + issueKey + "/attachments";
        org.apache.http.client.methods.HttpPost request = new org.apache.http.client.methods.HttpPost(url);
        request.setHeader(HttpHeaders.AUTHORIZATION, authHeader);
        request.setHeader(HttpHeaders.ACCEPT, "application/json");
        request.setHeader("X-Atlassian-Token", "no-check");

        org.apache.http.entity.mime.MultipartEntityBuilder builder = org.apache.http.entity.mime.MultipartEntityBuilder.create();
        builder.addBinaryBody("file", fileBytes, org.apache.http.entity.ContentType.DEFAULT_BINARY, fileName);
        request.setEntity(builder.build());

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            client.execute(request);
        }
    }

    public void addWatcher(String issueKey, String accountId) throws IOException {
        String url = baseUrl + "rest/api/latest/issue/" + issueKey + "/watchers";
        HttpPost request = new HttpPost(url);
        request.setHeader(HttpHeaders.AUTHORIZATION, authHeader);
        request.setHeader(HttpHeaders.ACCEPT, "application/json");
        request.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        request.setEntity(new StringEntity("\"" + accountId + "\"", StandardCharsets.UTF_8));
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            client.execute(request);
        }
    }
}
