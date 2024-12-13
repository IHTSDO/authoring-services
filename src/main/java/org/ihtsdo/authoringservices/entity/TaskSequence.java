package org.ihtsdo.authoringservices.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity(name = "task_sequence")
public class TaskSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @OneToOne
    @JoinColumn(name = "project_key", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Project project;

    private int sequence = 0;

    public TaskSequence() {

    }

    public TaskSequence(Project project, int sequence) {
        this.project = project;
        this.sequence = sequence;
    }

    @JsonIgnore
    public long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public int getSequence() {
        return sequence;
    }

    public void setSequence(int sequence) {
        this.sequence = sequence;
    }

    public void increase() {
        sequence++;
    }
}
