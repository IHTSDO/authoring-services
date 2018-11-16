package org.ihtsdo.snowowl.authoring.single.api.pojo;

import java.util.List;

import org.ihtsdo.snowowl.authoring.single.api.service.TaskStatus;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = AuthoringTask.class)
public interface AuthoringTaskUpdateRequest extends AuthoringTaskCreateRequest {

	TaskStatus getStatus();

	void setStatus(TaskStatus status);

	User getAssignee();

	void setAssignee(User assignee);

	List<User> getReviewers();

	void setReviewers(List<User> reviewer);
}
