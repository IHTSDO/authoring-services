package org.ihtsdo.authoringservices.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = AuthoringTask.class)
public interface AuthoringTaskCreateRequest {

	String getSummary();

	void setSummary(String title);

	String getDescription();

	void setDescription(String description);

	User getAssignee();

	void setAssignee(User assignee);

}
