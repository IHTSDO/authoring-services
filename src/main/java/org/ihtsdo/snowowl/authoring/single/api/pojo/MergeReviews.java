package org.ihtsdo.snowowl.authoring.single.api.pojo;

import org.ihtsdo.otf.rest.client.snowowl.pojo.MergeReviewsResults;
import org.ihtsdo.otf.rest.client.snowowl.pojo.MergeReviewsResults.MergeReviewStatus;

public class MergeReviews {
	private String message;
	private MergeReviewsResults results;
	
	public MergeReviews (MergeReviewsResults results) {
		this.results = results;
	}
	
	public MergeReviews (String errorMsg) {
		MergeReviewsResults runObj = new MergeReviewsResults();
		runObj.setStatus(MergeReviewsResults.MergeReviewStatus.FAILED);
		results = runObj;
		message = errorMsg;
	}
	
	public MergeReviewStatus getStatus() {
		return results.getStatus();

	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getId() {
		return results.getId();
	}
}
