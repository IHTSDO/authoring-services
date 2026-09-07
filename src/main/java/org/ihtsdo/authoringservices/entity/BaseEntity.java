package org.ihtsdo.authoringservices.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;

@MappedSuperclass
@JsonIgnoreProperties(ignoreUnknown = true)
public class BaseEntity {
    @Column(name = "created_timestamp")
    @CreationTimestamp
    private Timestamp createdDate;

    @Column(name = "updated_timestamp")
    @UpdateTimestamp
    private Timestamp updatedDate;

    public long getCreated() {
        return createdDate.getTime();
    }

    public long getUpdated() {
        return updatedDate.getTime();
    }
}
