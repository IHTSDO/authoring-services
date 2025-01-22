package org.ihtsdo.authoringservices.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import java.sql.Timestamp;

@MappedSuperclass
public class BaseEntity {
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
}
