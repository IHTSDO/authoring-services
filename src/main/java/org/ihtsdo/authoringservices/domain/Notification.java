package org.ihtsdo.authoringservices.domain;

import java.util.Collections;
import java.util.List;

public class Notification {

	private String project;
	private String task;
	private EntityType entityType;
	private String event;
	private String branchPath;
	private String notificationMessage;
	private String deepLinkPath;
	private NotificationSeverity severity;
	private List<String> requiresRefetch = Collections.emptyList();

	//Task level notification
	public Notification(String project, String task, EntityType entityType, String event) {
		this(project, entityType, event);
		this.task = task;
	}

	//Project level notification
	public Notification(String project, EntityType entityType, String event) {
		this.project = project;
		this.entityType = entityType;
		this.event = event;
	}

	public String getProject() {
		return project;
	}

	public void setProject(String project) {
		this.project = project;
	}

	public String getTask() {
		return task;
	}

	public void setTask(String task) {
		this.task = task;
	}

	public EntityType getEntityType() {
		return entityType;
	}

	public void setEntityType(EntityType entityType) {
		this.entityType = entityType;
	}

	public String getEvent() {
		return event;
	}

	public void setEvent(String event) {
		this.event = event;
	}

	public void setBranchPath(String branchPath) {
		this.branchPath = branchPath;
	}

	public String getBranchPath() {
		return branchPath;
	}

	public String getNotificationMessage() {
		return notificationMessage;
	}

	public void setNotificationMessage(String notificationMessage) {
		this.notificationMessage = notificationMessage;
	}

	public String getDeepLinkPath() {
		return deepLinkPath;
	}

	public void setDeepLinkPath(String deepLinkPath) {
		this.deepLinkPath = deepLinkPath;
	}

	public NotificationSeverity getSeverity() {
		return severity;
	}

	public void setSeverity(NotificationSeverity severity) {
		this.severity = severity;
	}

	public List<String> getRequiresRefetch() {
		return requiresRefetch;
	}

	public void setRequiresRefetch(List<String> requiresRefetch) {
		this.requiresRefetch = requiresRefetch != null ? requiresRefetch : Collections.emptyList();
	}

	@Override
	public String toString() {
		return "Notification{" +
				"project='" + project + '\'' +
				", task='" + task + '\'' +
				", branchPath='" + branchPath + '\'' +
				", entityType=" + entityType +
				", event='" + event + '\'' +
				", notificationMessage='" + notificationMessage + '\'' +
				", deepLinkPath='" + deepLinkPath + '\'' +
				", severity=" + severity +
				", requiresRefetch=" + requiresRefetch +
				'}';
	}
}
