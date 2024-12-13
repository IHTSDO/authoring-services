package org.ihtsdo.authoringservices.entity;


import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity(name = "crs_task")
public class CrsTask {

    @Id
    @Column(name = "crs_task_key")
    private String crsTaskKey;

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

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }
}
