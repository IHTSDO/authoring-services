package org.ihtsdo.authoringservices.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name="rvf_failure_jira_associations")
public class RVFFailureJiraAssociation {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonIgnore
	@Column(name="id")
	private long id;

	@Column(name="report_run_id")
	private Long reportRunId;

	@Column(name="assertion_id")
	private String assertionId;

	@Column(name="jira_url")
	private String jiraUrl;

	public RVFFailureJiraAssociation() {
	}

	public RVFFailureJiraAssociation(Long reportRunId, String assertionId, String jiraUrl) {
		this.reportRunId = reportRunId;
		this.assertionId = assertionId;
		this.jiraUrl = jiraUrl;
	}

	public long getId() {
		return id;
	}

	public void setReportRunId(Long reportRunId) {
		this.reportRunId = reportRunId;
	}

	public Long getReportRunId() {
		return reportRunId;
	}

	public String getAssertionId() {
		return assertionId;
	}

	public void setAssertionId(String assertionId) {
		this.assertionId = assertionId;
	}

	public String getJiraUrl() {
		return jiraUrl;
	}

	public void setJiraUrl(String jiraUrl) {
		this.jiraUrl = jiraUrl;
	}
}