package org.ihtsdo.authoringservices.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

@JsonDeserialize(as = AuthoringTask.class)
public interface AuthoringTaskUpdateRequest extends AuthoringTaskCreateRequest {

	TaskStatus getStatus();

	void setStatus(TaskStatus status);

	User getAssignee();

	void setAssignee(User assignee);

	List<User> getReviewers();

	void setReviewers(List<User> reviewer);
}
