package org.ihtsdo.authoringservices.entity;

import jakarta.persistence.*;
import org.ihtsdo.authoringservices.domain.RMPTaskStatus;

@Entity(name = "rmp_task")
public class RMPTask extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String type;

    @Enumerated(EnumType.STRING)
    private RMPTaskStatus status;

    private String country;

    private String reporter;

    private String assignee;

    @Column(columnDefinition = "TEXT DEFAULT NULL")
    private String summary;

    @Column(columnDefinition = "TEXT DEFAULT NULL")
    private String languageRefset;

    @Column(columnDefinition = "TEXT DEFAULT NULL")
    private String contextRefset;

    @Column(columnDefinition = "TEXT DEFAULT NULL")
    private String concept;

    private String conceptId;

    @Column(columnDefinition = "TEXT DEFAULT NULL")
    private String conceptName;

    private String relationshipType;

    private String relationshipTarget;

    @Column(columnDefinition = "TEXT DEFAULT NULL")
    private String existingRelationship;

    @Column(columnDefinition = "TEXT DEFAULT NULL")
    private String memberConceptIds;

    @Column(columnDefinition = "TEXT DEFAULT NULL")
    private String eclQuery;

    @Column(columnDefinition = "TEXT DEFAULT NULL")
    private String existingDescription;

    @Column(columnDefinition = "TEXT DEFAULT NULL")
    private String newDescription;

    @Column(columnDefinition = "TEXT DEFAULT NULL")
    private String newFSN;

    @Column(columnDefinition = "TEXT DEFAULT NULL")
    private String newPT;

    @Column(columnDefinition = "TEXT DEFAULT NULL")
    private String newSynonyms;

    private String parentConcept;

    @Column(columnDefinition = "TEXT DEFAULT NULL")
    private String justification;

    @Column(columnDefinition = "TEXT DEFAULT NULL")
    private String reference;

    public void setId(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public RMPTaskStatus getStatus() {
        return status;
    }

    public void setStatus(RMPTaskStatus status) {
        this.status = status;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getReporter() {
        return reporter;
    }

    public void setReporter(String reporter) {
        this.reporter = reporter;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getLanguageRefset() {
        return languageRefset;
    }

    public void setLanguageRefset(String languageRefset) {
        this.languageRefset = languageRefset;
    }

    public String getContextRefset() {
        return contextRefset;
    }

    public void setContextRefset(String contextRefset) {
        this.contextRefset = contextRefset;
    }

    public String getConcept() {
        return concept;
    }

    public void setConcept(String concept) {
        this.concept = concept;
    }

    public String getConceptId() {
        return conceptId;
    }

    public void setConceptId(String conceptId) {
        this.conceptId = conceptId;
    }

    public String getConceptName() {
        return conceptName;
    }

    public void setConceptName(String conceptName) {
        this.conceptName = conceptName;
    }

    public String getRelationshipType() {
        return relationshipType;
    }

    public void setRelationshipType(String relationshipType) {
        this.relationshipType = relationshipType;
    }

    public String getRelationshipTarget() {
        return relationshipTarget;
    }

    public void setRelationshipTarget(String relationshipTarget) {
        this.relationshipTarget = relationshipTarget;
    }

    public String getExistingRelationship() {
        return existingRelationship;
    }

    public void setExistingRelationship(String existingRelationship) {
        this.existingRelationship = existingRelationship;
    }

    public String getMemberConceptIds() {
        return memberConceptIds;
    }

    public void setMemberConceptIds(String memberConceptIds) {
        this.memberConceptIds = memberConceptIds;
    }

    public String getEclQuery() {
        return eclQuery;
    }

    public void setEclQuery(String eclQuery) {
        this.eclQuery = eclQuery;
    }

    public String getExistingDescription() {
        return existingDescription;
    }

    public void setExistingDescription(String existingDescription) {
        this.existingDescription = existingDescription;
    }

    public String getNewDescription() {
        return newDescription;
    }

    public void setNewDescription(String newDescription) {
        this.newDescription = newDescription;
    }

    public String getNewFSN() {
        return newFSN;
    }

    public void setNewFSN(String newFSN) {
        this.newFSN = newFSN;
    }

    public String getNewPT() {
        return newPT;
    }

    public void setNewPT(String newPT) {
        this.newPT = newPT;
    }

    public String getNewSynonyms() {
        return newSynonyms;
    }

    public void setNewSynonyms(String newSynonyms) {
        this.newSynonyms = newSynonyms;
    }

    public String getParentConcept() {
        return parentConcept;
    }

    public void setParentConcept(String parentConcept) {
        this.parentConcept = parentConcept;
    }

    public String getJustification() {
        return justification;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

}
