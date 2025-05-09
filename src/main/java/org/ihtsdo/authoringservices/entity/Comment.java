package org.ihtsdo.authoringservices.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "comment")
public class Comment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(columnDefinition = "TEXT DEFAULT NULL")
    private String body;

    private String user;

    @ManyToOne
    @JoinColumn(name = "rmp_task")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RMPTask rmpTask;

    public long getId() {
        return id;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    @JsonIgnore
    public RMPTask getRmpTask() {
        return rmpTask;
    }

    public void setRmpTask(RMPTask rmpTask) {
        this.rmpTask = rmpTask;
    }

}
