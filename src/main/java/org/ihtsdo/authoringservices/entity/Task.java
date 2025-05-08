package org.ihtsdo.authoringservices.entity;

import jakarta.persistence.*;
import org.ihtsdo.authoringservices.domain.TaskStatus;
import org.ihtsdo.authoringservices.domain.TaskType;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity(name = "task")
public class  Task {

    @Id
    @Column(name = "task_key", nullable = false)
    private String key;

    @Column(name = "task_name", nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT DEFAULT NULL")
    private String description;

    @Column(name = "branch_path")
    private String branchPath;

    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private TaskType type;

    @ManyToOne
    @JoinColumn(name = "project_key", nullable = false)
    private Project project;

    private String assignee;

    @Column(nullable = false)
    private String reporter;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<CrsTask> crsTasks;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<TaskReviewer> reviewers = new ArrayList<>();

    @Column(name = "created_timestamp")
    @Temporal(TemporalType.TIMESTAMP)
    // Cannot use this annotation @CreationTimestamp for now as we need to sync the jira tasks due to migration.
    //@CreationTimestamp
    private Timestamp createdDate;

    @Column(name = "updated_timestamp")
    @Temporal(TemporalType.TIMESTAMP)
    // Cannot use this annotation  @UpdateTimestamp for now as we need to sync the jira tasks due to migration.
    // @UpdateTimestamp
    private Timestamp updatedDate;

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBranchPath() {
        return branchPath;
    }

    public void setBranchPath(String branchPath) {
        this.branchPath = branchPath;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public TaskType getType() {
        return type;
    }

    public void setType(TaskType type) {
        this.type = type;
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

    public List<CrsTask> getCrsTasks() {
        return crsTasks;
    }

    public void setCrsTasks(List<CrsTask> crsTasks) {
        this.crsTasks = crsTasks;
    }

    public List<TaskReviewer> getReviewers() {
        return reviewers;
    }

    public void setReviewers(List<TaskReviewer> reviewers) {
        this.reviewers = reviewers;
    }

    public long getCreated() {
        return createdDate.getTime();
    }

    public long getUpdated() {
        return updatedDate.getTime();
    }

    public void setCreatedDate(Timestamp createdDate) {
        this.createdDate = createdDate;
    }

    public void setUpdatedDate(Timestamp updatedDate) {
        this.updatedDate = updatedDate;
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
