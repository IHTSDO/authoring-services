package org.ihtsdo.authoringservices.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;

@Entity
public class Validation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonIgnore
	private long id;

	@Column(name = "run_id")
	private Long runId;

	@Column(name = "branch_path", unique = true)
	private String branchPath;

	@Column(name = "status")
	private String status;

	@Column(name = "content_head_timestamp")
	private Long contentHeadTimestamp;

	@Column(name = "report_url")
	private String reportUrl;

	protected Validation() {
	}

	public Validation(String branchPath, String status) {
		this.branchPath = branchPath;
		this.status = status;
	}

	public long getId() {
		return id;
	}

	public Long getRunId() {
		return runId;
	}

	public void setRunId(Long runId) {
		this.runId = runId;
	}

	public String getBranchPath() {
		return branchPath;
	}

	public void setBranchPath(String branchPath) {
		this.branchPath = branchPath;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Long getContentHeadTimestamp() {
		return contentHeadTimestamp;
	}

	public void setContentHeadTimestamp(Long contentHeadTimestamp) {
		this.contentHeadTimestamp = contentHeadTimestamp;
	}

	public void setReportUrl(String reportUrl) {
		this.reportUrl = reportUrl;
	}

	public String getReportUrl() {
		return reportUrl;
	}

	@Override
	public String toString() {
		return "Validation{" +
				"id=" + id +
				", runId='" + runId + '\'' +
				", branchPath='" + branchPath + '\'' +
				", status=" + status +
				", contentHeadTimestamp=" + contentHeadTimestamp +
				", reportUrl=" + reportUrl +
				'}';
	}
}

