package org.ihtsdo.authoringservices.entity;

import jakarta.persistence.*;
import org.ihtsdo.authoringservices.domain.TaskStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity(name = "task")
public class Task {

    @Id
    @Column(name = "task_key", nullable = false)
    private String key;

    @Column(name = "task_name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    @ManyToOne
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

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Task task)) return false;
        return Objects.equals(getKey(), task.getKey()) && Objects.equals(getProject(), task.getProject());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getKey(), getProject());
    }
}
