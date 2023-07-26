package org.ihtsdo.authoringservices.domain;

public class ValidationConfiguration {

    private String branchPath;
    private String previousRelease;
    private String assertionGroupNames;
    private String failureExportMax = "100";
    private String dependencyRelease;
    private String productName;
    private String releaseDate;
    private String releaseCenter;
    private String previousPackage;
    private String dependencyPackage;
    private String defaultModuleId;
    private String includedModuleIds;
    private boolean enableMRCMValidation;
    private boolean enableDroolsValidation;
    private boolean enableTraceabilityValidation;
    private Long contentHeadTimestamp;

    private Long contentBaseTimestamp;
    private String projectKey;
    private String taskKey;

    public String checkMissingParameters() {
        StringBuilder msgBuilder = new StringBuilder();
        if (this.assertionGroupNames == null) {
            msgBuilder.append("assertionGroupNames can't be null.");
        }
        if (dependencyRelease == null && previousRelease == null) {
            if (msgBuilder.length() > 0) {
                msgBuilder.append(" ");
            }
            msgBuilder.append("previousRelease and dependencyRelease can't be both null.");
        }

        if (previousPackage == null && dependencyPackage == null) {
            if (msgBuilder.length() > 0) {
                msgBuilder.append(" ");
            }
            msgBuilder.append("previousPackage and dependencyPackage can't be both null.");
        }
        if (!msgBuilder.toString().isEmpty()) {
            return msgBuilder.toString();
        }
        return null;
    }

    public String getBranchPath() {
        return branchPath;
    }

    public void setBranchPath(String branchPath) {
        this.branchPath = branchPath;
    }

    public String getPreviousPackage() {
        return previousPackage;
    }

    public void setPreviousPackage(String previousPackage) {
        this.previousPackage = previousPackage;
    }

    public String getDependencyPackage() {
        return dependencyPackage;
    }

    public void setDependencyPackage(String dependencyPackage) {
        this.dependencyPackage = dependencyPackage;
    }

    public String getDependencyRelease() {
        return dependencyRelease;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public void setReleaseDate(String releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getPreviousRelease() {
        return previousRelease;
    }

    public void setPreviousRelease(String previousRelease) {
        this.previousRelease = previousRelease;
    }

    public String getAssertionGroupNames() {
        return assertionGroupNames;
    }

    public void setAssertionGroupNames(String assertionGroupNames) {
        this.assertionGroupNames = assertionGroupNames;
    }

    public String getFailureExportMax() {
        return failureExportMax;
    }

    public void setFailureExportMax(String failureExportMax) {
        this.failureExportMax = failureExportMax;
    }

    public void setDependencyRelease(String extensionDependencyRelease) {
        this.dependencyRelease = extensionDependencyRelease;
    }

    public String getProductName() {
        return this.productName;
    }

    public String getReleaseDate() {
        return this.releaseDate;
    }

    public void setReleaseCenter(String releaseCenter) {
        this.releaseCenter = releaseCenter;
    }

    public String getReleaseCenter() {
        return this.releaseCenter;
    }

    public String getDefaultModuleId() {
        return defaultModuleId;
    }

    public void setDefaultModuleId(String defaultModuleId) {
        this.defaultModuleId = defaultModuleId;
    }

    public String getIncludedModuleIds() {
        return includedModuleIds;
    }

    public void setIncludedModuleIds(String includedModuleIds) {
        this.includedModuleIds = includedModuleIds;
    }

    public boolean isEnableMRCMValidation() {
        return enableMRCMValidation;
    }

    public boolean isEnableDroolsValidation() {
        return enableDroolsValidation;
    }

    public void setEnableDroolsValidation(boolean enableDroolsValidation) {
        this.enableDroolsValidation = enableDroolsValidation;
    }

    public void setEnableMRCMValidation(boolean enableMRCMValidation) {
        this.enableMRCMValidation = enableMRCMValidation;
    }

	public boolean isEnableTraceabilityValidation() {
		return enableTraceabilityValidation;
	}

	public void setEnableTraceabilityValidation(boolean enableTraceabilityValidation) {
		this.enableTraceabilityValidation = enableTraceabilityValidation;
	}

	public void setContentHeadTimestamp(Long contentHeadTimestamp) {
        this.contentHeadTimestamp = contentHeadTimestamp;
    }

    public Long getContentHeadTimestamp() {
        return contentHeadTimestamp;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setTaskKey(String taskKey) {
        this.taskKey = taskKey;
    }

    public String getTaskKey() {
        return taskKey;
    }

    public Long getContentBaseTimestamp() {
        return contentBaseTimestamp;
    }

    public void setContentBaseTimestamp(Long contentBaseTimestamp) {
        this.contentBaseTimestamp = contentBaseTimestamp;
    }

    @Override
    public String toString() {
        return "ValidationConfiguration [productName=" + productName
                + ", releaseDate=" + releaseDate + ", assertionGroupNames="
                + assertionGroupNames + ", rvfDroolsAssertionGroupNames="
                + failureExportMax + ", previousRelease="
                + previousRelease + ", dependencyRelease="
                + dependencyRelease + ",releaseCenter="
                + releaseCenter + ",includedModuleIds="
                + includedModuleIds + "]";
    }
}

