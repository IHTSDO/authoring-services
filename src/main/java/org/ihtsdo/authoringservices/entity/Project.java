package org.ihtsdo.authoringservices.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.*;

@Entity(name = "project")
public class Project extends BaseEntity {

    @Id
    @Column(name = "project_key", nullable = false)
    private String key;

    @Column(name = "project_name", nullable = false)
    private String name;

    @Column(name = "project_lead")
    private String lead;

    @Column(name = "branch_path")
    private String branchPath;

    @Column(name = "extension_base")
    private String extensionBase;

    private Boolean active = true;

    @Column(name = "custom_fields")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Boolean> customFields = new HashMap<>();

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    private TaskSequence taskSequence;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ProjectUserGroup> userGroups = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Task> tasks = new ArrayList<>();

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

    public String getLead() {
        return lead;
    }

    public void setLead(String lead) {
        this.lead = lead;
    }

    public String getBranchPath() {
        return branchPath;
    }

    public void setBranchPath(String branchPath) {
        this.branchPath = branchPath;
    }

    public String getExtensionBase() {
        return extensionBase;
    }

    public void setExtensionBase(String extensionBase) {
        this.extensionBase = extensionBase;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Boolean getActive() {
        return active;
    }

    public Map<String, Boolean> getCustomFields() {
        return customFields;
    }

    public void setCustomFields(Map<String, Boolean> customFields) {
        this.customFields = customFields;
    }

    public TaskSequence getTaskSequence() {
        return taskSequence;
    }

    public List<ProjectUserGroup> getUserGroups() {
        return userGroups;
    }

    public void setUserGroups(List<ProjectUserGroup> userGroups) {
        this.userGroups = userGroups;
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Project project)) return false;
        return Objects.equals(getKey(), project.getKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getKey());
    }
}
