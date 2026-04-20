package org.ihtsdo.authoringservices.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "rmp_task_attachment")
public class RMPTaskAttachment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "content_size")
    private long contentSize;

    @Lob
    @Column(name = "content", columnDefinition = "LONGBLOB")
    @JsonIgnore
    private byte[] content;

    private String user;

    @ManyToOne
    @JoinColumn(name = "rmp_task")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private RMPTask rmpTask;

    public long getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getContentSize() {
        return contentSize;
    }

    public void setContentSize(long contentSize) {
        this.contentSize = contentSize;
    }

    @JsonIgnore
    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
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
