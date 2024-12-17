package org.ihtsdo.authoringservices.domain;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.CodeSystem;

import java.util.Map;
import java.util.Objects;

@JsonDeserialize
public class AuthoringProject {

    private String key;
    private String title;
    private User projectLead;
    private String branchPath;
    private String branchState;
    private String latestClassificationJson;
    private String validationStatus;
    private Long branchHeadTimestamp;
    private Long branchBaseTimestamp;
    private Boolean projectPromotionDisabled;
    private Boolean projectLocked;
    private Boolean projectRebaseDisabled;
    private Boolean projectMrcmDisabled;
    private Boolean projectTemplatesDisabled;
    private Boolean projectSpellCheckDisabled;
    private Boolean projectScheduledRebaseDisabled;
    private Boolean taskPromotionDisabled;
    private Map<String, Object> metadata;
    private CodeSystem codeSystem;

    private boolean internalAuthoringProject;

    public AuthoringProject() {
    }

    public AuthoringProject(String key, String title, User leadUser, String branchPath, String branchState, Long baseTimeStamp, Long headTimeStamp,
                            String latestClassificationJson, boolean projectPromotionDisabled,
                            boolean projectMrcmDisabled, boolean projectTemplatesDisabled, boolean projectSpellCheckDisabled, boolean projectRebaseDisabled,
                            boolean projectScheduledRebaseDisabled,
                            boolean taskPromotionDisabled,
                            boolean projectLocked) {
        this.key = key;
        this.title = title;
        this.projectLead = leadUser;
        this.branchPath = branchPath;
        this.branchState = branchState;
        this.branchBaseTimestamp = baseTimeStamp;
        this.branchHeadTimestamp = headTimeStamp;
        this.latestClassificationJson = latestClassificationJson;
        this.projectPromotionDisabled = projectPromotionDisabled;
        this.projectMrcmDisabled = projectMrcmDisabled;
        this.projectTemplatesDisabled = projectTemplatesDisabled;
        this.projectSpellCheckDisabled = projectSpellCheckDisabled;
        this.projectRebaseDisabled = projectRebaseDisabled;
        this.projectScheduledRebaseDisabled = projectScheduledRebaseDisabled;
        this.taskPromotionDisabled = taskPromotionDisabled;
        this.projectLocked = projectLocked;
    }

    public String getKey() {
        return key;
    }

    public String getTitle() {
        return title;
    }

    public User getProjectLead() {
        return projectLead;
    }

    public void setBranchPath(String branchPath) {
        this.branchPath = branchPath;
    }

    public String getBranchPath() {
        return branchPath;
    }

    public String getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(String validationStatus) {
        this.validationStatus = validationStatus;
    }

    @JsonRawValue
    public String getLatestClassificationJson() {
        return latestClassificationJson;
    }

    public Long getBranchHeadTimestamp() {
        return branchHeadTimestamp;
    }

    public void setBranchHeadTimestamp(Long branchHeadTimestamp) {
        this.branchHeadTimestamp = branchHeadTimestamp;
    }

    public Long getBranchBaseTimestamp() {
        return branchBaseTimestamp;
    }

    public void setBranchBaseTimestamp(Long branchBaseTimestamp) {
        this.branchBaseTimestamp = branchBaseTimestamp;
    }

    public String getBranchState() {
        return branchState;
    }

    public Boolean isProjectPromotionDisabled() {
        return projectPromotionDisabled;
    }

    public void setProjectPromotionDisabled(Boolean projectPromotionDisabled) {
        this.projectPromotionDisabled = projectPromotionDisabled;
    }

    public Boolean isProjectRebaseDisabled() {
        return projectRebaseDisabled;
    }

    public void setProjectRebaseDisabled(Boolean projectRebaseDisabled) {
        this.projectRebaseDisabled = projectRebaseDisabled;
    }

    public Boolean isProjectMrcmDisabled() {
        return projectMrcmDisabled;
    }

    public void setProjectMrcmDisabled(Boolean projectMrcmDisabled) {
        this.projectMrcmDisabled = projectMrcmDisabled;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Boolean isProjectTemplatesDisabled() {
        return projectTemplatesDisabled;
    }

    public void setProjectTemplatesDisabled(Boolean projectTemplatesDisabled) {
        this.projectTemplatesDisabled = projectTemplatesDisabled;
    }

    public Boolean isProjectSpellCheckDisabled() {
        return projectSpellCheckDisabled;
    }

    public Boolean isProjectScheduledRebaseDisabled() {
        return projectScheduledRebaseDisabled;
    }

    public void setProjectScheduledRebaseDisabled(Boolean projectScheduledRebaseDisabled) {
        this.projectScheduledRebaseDisabled = projectScheduledRebaseDisabled;
    }

    public void setProjectSpellCheckDisabled(Boolean projectSpellCheckDisabled) {
        this.projectSpellCheckDisabled = projectSpellCheckDisabled;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }


    public Boolean isTaskPromotionDisabled() {
        return taskPromotionDisabled;
    }

    public void setTaskPromotionDisabled(Boolean taskPromotionDisabled) {
        this.taskPromotionDisabled = taskPromotionDisabled;
    }

    public Boolean isProjectLocked() {
        return projectLocked;
    }

    public void setCodeSystem(CodeSystem codeSystem) {
        this.codeSystem = codeSystem;
    }

    public CodeSystem getCodeSystem() {
        return codeSystem;
    }

    public void setInternalAuthoringProject(boolean internalAuthoringProject) {
        this.internalAuthoringProject = internalAuthoringProject;
    }

    public boolean isInternalAuthoringProject() {
        return internalAuthoringProject;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuthoringProject project)) return false;
        return isInternalAuthoringProject() == project.isInternalAuthoringProject() && Objects.equals(getKey(), project.getKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getKey(), isInternalAuthoringProject());
    }
}
