package org.ihtsdo.authoringservices.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.util.Objects;

@Entity(name = "task_reviewer")
@Table(uniqueConstraints={
        @UniqueConstraint(columnNames = {"task_key", "username"})
})
public class TaskReviewer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "task_key")
    private Task task;


    @Column(nullable = false)
    private String username;

    public TaskReviewer() {
    }

    public TaskReviewer(Task task, String username) {
        this.task = task;
        this.username = username;
    }

    @JsonIgnore
    public long getId() {
        return id;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TaskReviewer reviewer)) return false;
        return Objects.equals(getTask(), reviewer.getTask()) && Objects.equals(getUsername(), reviewer.getUsername());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTask(), getUsername());
    }
}
