package org.ihtsdo.snowowl.authoring.single.api.pojo;

public class ProcessStatus {
	String status;
	String message;
	
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
	
}
