package org.ihtsdo.authoringservices.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.*;

@Entity(name = "project")
public class Project {

    @Id
    @Column(name = "project_key", nullable = false)
    private String key;

    @Column(name = "project_name", nullable = false)
    private String name;

    @Column(name = "project_lead")
    private String lead;

    @Column(name = "custom_fields")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Boolean> customFields = new HashMap<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ProjectGroup> groups = new ArrayList<>();

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

    public Map<String, Boolean> getCustomFields() {
        return customFields;
    }

    public void setCustomFields(Map<String, Boolean> customFields) {
        this.customFields = customFields;
    }

    public List<ProjectGroup> getGroups() {
        return groups;
    }

    public void setGroups(List<ProjectGroup> groups) {
        this.groups = groups;
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
