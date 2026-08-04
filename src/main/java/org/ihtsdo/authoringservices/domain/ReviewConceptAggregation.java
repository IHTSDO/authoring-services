package org.ihtsdo.authoringservices.domain;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ReviewConceptAggregation {

	public static final String CHANGE_TYPE_CONTENT = "CONTENT";
	public static final String CHANGE_TYPE_CLASSIFICATION = "CLASSIFICATION";

	private List<AggregatedReviewConcept> concepts = new ArrayList<>();
	private int totalChangedConcepts;
	private int totalReviewed;

	public List<AggregatedReviewConcept> getConcepts() {
		return concepts;
	}

	public void setConcepts(List<AggregatedReviewConcept> concepts) {
		this.concepts = concepts != null ? concepts : new ArrayList<>();
	}

	public int getTotalChangedConcepts() {
		return totalChangedConcepts;
	}

	public void setTotalChangedConcepts(int totalChangedConcepts) {
		this.totalChangedConcepts = totalChangedConcepts;
	}

	public int getTotalReviewed() {
		return totalReviewed;
	}

	public void setTotalReviewed(int totalReviewed) {
		this.totalReviewed = totalReviewed;
	}

	public static class AggregatedReviewConcept {
		private String conceptId;
		private String fsn;
		private String pt;
		private String changeType;
		private List<FeedbackItem> feedback = new ArrayList<>();
		private boolean reviewed;

		public String getConceptId() {
			return conceptId;
		}

		public void setConceptId(String conceptId) {
			this.conceptId = conceptId;
		}

		public String getFsn() {
			return fsn;
		}

		public void setFsn(String fsn) {
			this.fsn = fsn;
		}

		public String getPt() {
			return pt;
		}

		public void setPt(String pt) {
			this.pt = pt;
		}

		public String getChangeType() {
			return changeType;
		}

		public void setChangeType(String changeType) {
			this.changeType = changeType;
		}

		public List<FeedbackItem> getFeedback() {
			return feedback;
		}

		public void setFeedback(List<FeedbackItem> feedback) {
			this.feedback = feedback != null ? feedback : new ArrayList<>();
		}

		public boolean isReviewed() {
			return reviewed;
		}

		public void setReviewed(boolean reviewed) {
			this.reviewed = reviewed;
		}
	}

	public static class FeedbackItem {
		private String message;
		private String author;
		private Date date;

		public FeedbackItem() {
		}

		public FeedbackItem(String message, String author, Date date) {
			this.message = message;
			this.author = author;
			this.date = date;
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

		public String getAuthor() {
			return author;
		}

		public void setAuthor(String author) {
			this.author = author;
		}

		public Date getDate() {
			return date;
		}

		public void setDate(Date date) {
			this.date = date;
		}
	}
}
