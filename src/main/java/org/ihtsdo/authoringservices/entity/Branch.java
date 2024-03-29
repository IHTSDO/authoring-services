package org.ihtsdo.authoringservices.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Branch {

	@Id
	@GeneratedValue(strategy= GenerationType.IDENTITY)
	@JsonIgnore
	private long id;
	private String project;
	private String task;

	protected Branch() {
	}

	public Branch(String project, String task) {
		this.project = project;
		this.task = task;
	}

	public Branch(String project) {
		this.project = project;
	}

	public long getId() {
		return id;
	}

	public String getProject() {
		return project;
	}

	public String getTask() {
		return task;
	}
}
