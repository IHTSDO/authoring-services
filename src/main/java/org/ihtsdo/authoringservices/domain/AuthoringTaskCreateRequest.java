package org.ihtsdo.authoringservices.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.ihtsdo.authoringservices.entity.CrsTask;

import java.util.List;

@JsonDeserialize(as = AuthoringTask.class)
public interface AuthoringTaskCreateRequest {

	String getSummary();

	void setSummary(String title);

	String getDescription();

	void setDescription(String description);

	User getAssignee();

	void setAssignee(User assignee);

	void setCrsTasks(List<CrsTask> crsTasks);

	List<CrsTask> getCrsTasks();

}
