package org.ihtsdo.authoringservices.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.rcarz.jiraclient.Issue;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.ihtsdo.authoringservices.entity.Task;
import org.ihtsdo.otf.rest.client.terminologyserver.PathHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AuthoringTask implements AuthoringTaskCreateRequest, AuthoringTaskUpdateRequest {

    public static final String JIRA_CREATED_FIELD = "created";
    public static final String JIRA_UPDATED_FIELD = "updated";

    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    private String key;
    private String projectKey;
    private String summary;
    private TaskStatus status;
    private String branchState;
    private Long branchHeadTimestamp;
    private Long branchBaseTimestamp;
    private Long latestCodeSystemVersionTimestamp;
    private String description;
    private User assignee;
    private User reporter;
    private List<User> reviewers;
    private String created;
    private String updated;
    private String latestClassificationJson;
    private String latestValidationStatus;
    private TaskMessagesStatus feedbackMessagesStatus;
    private Date feedbackMessageDate;
    private Date viewDate;
    private String branchPath;
    private String labels;

    public AuthoringTask() {
    }

    public AuthoringTask(Issue issue, String extensionBase, String jiraReviewerField, String jiraReviewersField) {
        key = issue.getKey();
        projectKey = issue.getProject().getKey();
        summary = issue.getSummary();
        status = TaskStatus.fromLabel(issue.getStatus().getName());
        description = issue.getDescription();
        net.rcarz.jiraclient.User user = issue.getAssignee();
        if (user != null) {
            this.assignee = new User(user);
        }
        net.rcarz.jiraclient.User issueReporter = issue.getReporter();
        if (issueReporter != null) {
            this.reporter = new User(issueReporter);
        }
        created = (String) issue.getField(JIRA_CREATED_FIELD);
        updated = (String) issue.getField(JIRA_UPDATED_FIELD);

        // declare the object mapper for JSON conversion
        ObjectMapper mapper = new ObjectMapper();

        // set the labels
        try {
            labels = mapper.writeValueAsString(issue.getLabels());
        } catch (JsonProcessingException e) {
            labels = "Failed to convert Jira labels into json string";
        }

        // set the reviewer object
        reviewers = new ArrayList<>();
        Object reviewerObj = issue.getField(jiraReviewerField);
        if (reviewerObj instanceof JSONObject obj) {
            reviewers.add(new User(obj));
        }

        Object reviewersObj = issue.getField(jiraReviewersField);
        if (reviewersObj instanceof JSONArray array && (!array.isEmpty())) {
            array.forEach(item -> reviewers.add(new User((JSONObject) item)));
        }

        branchPath = PathHelper.getTaskPath(extensionBase, projectKey, key);
    }

    public AuthoringTask(Task task) {
        key = task.getKey();
        projectKey = task.getProject().getKey();
        summary = task.getName();
        status = task.getStatus();
        description = task.getDescription();
        created = formatter.format(new Date(task.getCreated()));
        updated = formatter.format(new Date(task.getUpdated()));
        branchPath = task.getBranchPath();
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    @Override
    public String getSummary() {
        return summary;
    }

    @Override
    public void setSummary(String summary) {
        this.summary = summary;
    }

    @JsonProperty("status")
    public String getStatusName() {
        return status != null ? status.getLabel() : null;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public User getAssignee() {
        return assignee;
    }

    @Override
    public void setAssignee(User assignee) {
        this.assignee = assignee;
    }

    public User getReporter() {
        return reporter;
    }

    public void setReporter(User reporter) {
        this.reporter = reporter;
    }

    public String getCreated() {
        return created;
    }

    public void setCreated(String created) {
        this.created = created;
    }

    public String getUpdated() {
        return updated;
    }

    public void setUpdated(String updated) {
        this.updated = updated;
    }

    @JsonRawValue
    public String getLatestClassificationJson() {
        return latestClassificationJson;
    }

    public void setLatestClassificationJson(String json) {
        latestClassificationJson = json;
    }

    public void setLatestValidationStatus(String latestValidationStatus) {
        this.latestValidationStatus = latestValidationStatus;
    }

    public String getLatestValidationStatus() {
        return "".equals(latestValidationStatus) ? null : latestValidationStatus;
    }

    public List<User> getReviewers() {
        return reviewers;
    }

    public void setReviewers(List<User> reviewers) {
        this.reviewers = reviewers;
    }

    public void setFeedbackMessagesStatus(TaskMessagesStatus unreadFeedbackMessages) {
        this.feedbackMessagesStatus = unreadFeedbackMessages;
    }

    public TaskMessagesStatus getFeedbackMessagesStatus() {
        return feedbackMessagesStatus;
    }

    public void setBranchState(String branchState) {
        this.branchState = branchState;
    }

    public String getBranchState() {
        return branchState;
    }

    public Long getBranchHeadTimestamp() {
        return branchHeadTimestamp;
    }

    public void setBranchHeadTimestamp(Long branchHeadTimestamp) {
        this.branchHeadTimestamp = branchHeadTimestamp;
    }

    public Long getBranchBaseTimestamp() {
        return branchBaseTimestamp;
    }

    public void setBranchBaseTimestamp(Long branchBaseTimestamp) {
        this.branchBaseTimestamp = branchBaseTimestamp;
    }

    public Long getLatestCodeSystemVersionTimestamp() {
        return latestCodeSystemVersionTimestamp;
    }

    public void setLatestCodeSystemVersionTimestamp(Long latestCodeSystemVersionTimestamp) {
        this.latestCodeSystemVersionTimestamp = latestCodeSystemVersionTimestamp;
    }

    public String getBranchPath() {
        return branchPath;
    }

    public Date getFeedbackMessageDate() {
        return feedbackMessageDate;
    }

    public void setFeedbackMessageDate(Date feedbackMessageDate) {
        this.feedbackMessageDate = feedbackMessageDate;
    }

    public Date getViewDate() {
        return viewDate;
    }

    public void setViewDate(Date viewDate) {
        this.viewDate = viewDate;
    }


    @JsonRawValue
    public String getLabels() {
        return labels;
    }

    public void setLabels(String labels) {
        this.labels = labels;
    }


}
