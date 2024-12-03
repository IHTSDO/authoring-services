package org.ihtsdo.authoringservices.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity(name = "task")
public class Task {

    @Id
    @Column(name = "task_key", nullable = false)
    private String key;

    @Column(name = "task_name", nullable = false)
    private String name;

    private String status;

    @OneToOne
    @JoinColumn(name = "project_key", nullable = false)
    private Project project;

    private String assignee;

    @Column(nullable = false)
    private String reporter;

    @Column(name = "crs_task_id")
    private String crsTaskId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<TaskReviewer> reviewers = new ArrayList<>();

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public String getReporter() {
        return reporter;
    }

    public void setReporter(String reporter) {
        this.reporter = reporter;
    }

    public String getCrsTaskId() {
        return crsTaskId;
    }

    public void setCrsTaskId(String crsTaskId) {
        this.crsTaskId = crsTaskId;
    }

    public List<TaskReviewer> getReviewers() {
        return reviewers;
    }

    public void setReviewers(List<TaskReviewer> reviewers) {
        this.reviewers = reviewers;
    }
}
