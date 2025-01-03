package org.ihtsdo.authoringservices.entity;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity(name = "crs_task")
public class CrsTask {

    @Id
    @Column(name = "crs_task_key")
    private String crsTaskKey;

    @Column(name = "crs_jira_key")
    private String crsJiraKey;

    @ManyToOne
    @JoinColumn(name = "task_key")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Task task;

    public String getCrsTaskKey() {
        return crsTaskKey;
    }

    public void setCrsTaskKey(String crsTaskKey) {
        this.crsTaskKey = crsTaskKey;
    }

    public String getCrsJiraKey() {
        return crsJiraKey;
    }

    public void setCrsJiraKey(String crsJiraKey) {
        this.crsJiraKey = crsJiraKey;
    }

    @JsonIgnore
    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }
}
