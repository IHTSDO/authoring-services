package org.ihtsdo.authoringservices.domain;


import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ClassificationResults;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ClassificationStatus;

public class Classification {

	private String message;
	private ClassificationResults results;
	
	public Classification(ClassificationResults results) {
		this.results = results;
	}
	
	public Classification(String errorMsg) {
		ClassificationResults runObj = new ClassificationResults();
		runObj.setStatus(ClassificationStatus.FAILED);
		results = runObj;
		message = errorMsg;
	}

	public ClassificationStatus getStatus() {
		return results.getStatus();

	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getId() {
		return results.getClassificationId();
	}

	public ClassificationResults getResults() {
		return results;
	}

	public void setResults(ClassificationResults results) {
		this.results = results;
	}
	
}
