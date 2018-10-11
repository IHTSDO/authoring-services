package org.ihtsdo.snowowl.authoring.single.api.pojo;

import java.util.Date;

public class ProcessStatus {
	private String status;
	private String message;
	private Date completeDate;
	
	public ProcessStatus(String status, String message) {
		this.status = status;
		this.message = message;
	}
	
	public ProcessStatus() {}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public Date getCompleteDate() {
		return completeDate;
	}

	public void setCompleteDate(Date completeDate) {
		this.completeDate = completeDate;
	}
	
}
