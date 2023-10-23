package org.ihtsdo.authoringservices.entity;

import java.util.Date;
import java.util.List;
import jakarta.persistence.*;

@Entity
public class ReviewMessage {

	@Id
	@GeneratedValue(strategy= GenerationType.IDENTITY)
	private long id;
	@ManyToOne
	private Branch branch;
	@Column(columnDefinition = "TEXT DEFAULT NULL")
	private String messageHtml;
	private Date creationDate;
	private String fromUsername;
	private boolean feedbackRequested;

	@ElementCollection(fetch = FetchType.EAGER)
	private List<String> subjectConceptIds;

	protected ReviewMessage() {
		creationDate = new Date();
	}

	public ReviewMessage(Branch branch, String messageHtml, List<String> subjectConceptIds, boolean feedbackRequested, String fromUsername) {
		this();
		this.branch = branch;
		this.messageHtml = messageHtml;
		this.subjectConceptIds = subjectConceptIds;
		this.feedbackRequested = feedbackRequested;
		this.fromUsername = fromUsername;
	}

	public long getId() {
		return id;
	}

	public Date getCreationDate() {
		return creationDate;
	}
	public String getFromUsername() {
		return fromUsername;
	}

	public String getMessageHtml() {
		return messageHtml;
	}

	public List<String> getSubjectConceptIds() {
		return subjectConceptIds;
	}

	public boolean isFeedbackRequested() {
		return feedbackRequested;
	}

	public Branch getBranch() {
		return branch;
	}
}
