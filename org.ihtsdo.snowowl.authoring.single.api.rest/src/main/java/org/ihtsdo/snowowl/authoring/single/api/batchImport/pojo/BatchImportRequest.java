package org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo;

import java.util.UUID;

import org.ihtsdo.snowowl.authoring.single.api.batchImport.service.BatchImportFormat;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class BatchImportRequest {

	UUID id;
	String createForAuthor;
	int conceptsPerTask;
	BatchImportFormat format;
	String projectKey;
	String originalFilename;
	MultipartFile file;
	Integer postTaskDelay;
	Boolean dryRun;

	public BatchImportRequest(){}
	
	public String getCreateForAuthor() {
		return createForAuthor;
	}
	public void setCreateForAuthor(String createForAuthor) {
		this.createForAuthor = createForAuthor;
	}
	public int getConceptsPerTask() {
		return conceptsPerTask;
	}
	public void setConceptsPerTask(int conceptsPerTask) {
		this.conceptsPerTask = conceptsPerTask;
	}
	public BatchImportFormat getFormat() {
		return format;
	}
	@JsonIgnore
	public void setFormat(BatchImportFormat format) {
		this.format = format;
	}
	@JsonIgnore
	public String getProjectKey() {
		return projectKey;
	}
	public void setProjectKey(String projectKey) {
		this.projectKey = projectKey;
	}

	public MultipartFile getFile() {
		return file;
	}

	public void setFile(MultipartFile file) {
		this.file = file;
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public void setOriginalFilename(String originalFilename) {
		this.originalFilename = originalFilename;	
	}
	
	public String getOriginalFilename() {
		return this.originalFilename;
	}
	
	public Integer getPostTaskDelay() {
		return postTaskDelay;
	}

	public void setPostTaskDelay(Integer postTaskDelay) {
		this.postTaskDelay = postTaskDelay;
	}

	public Boolean getDryRun() {
		return dryRun;
	}

	public void setDryRun(Boolean dryRun) {
		this.dryRun = dryRun;
	}
}
