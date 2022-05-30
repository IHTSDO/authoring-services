package org.ihtsdo.authoringservices.domain;


public class TaskAttachment {

	// the attachment content
	public String content;
	
	// ticket key for which attachment was collected
	public String ticketKey;
	
	// linked ticket key (if exists)
	public String issueKey;

	// linked Organization (if exists)
	public String organization;

	public TaskAttachment() {
	}

	public TaskAttachment(String ticketKey, String issueKey, String content, String organization) {
		this.ticketKey = ticketKey;
		this.issueKey = issueKey;
		this.content = content;
		this.organization = organization;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}
	
	public String getTicketKey() {
		return ticketKey;
	}

	public void setTicketKey(String ticketKey) {
		this.ticketKey = ticketKey;
	}

	public String getIssueKey() {
		return issueKey;
	}

	public void setIssueKey(String issueKey) {
		this.issueKey = issueKey;
	}

	public void setOrganization(String organization) {
		this.organization = organization;
	}

	public String getOrganization() {
		return organization;
	}
}
