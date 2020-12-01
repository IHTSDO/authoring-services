package org.ihtsdo.snowowl.authoring.single.api.service;

import com.amazonaws.services.s3.model.AmazonS3Exception;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.TaskTransferRequest;
import org.ihtsdo.snowowl.authoring.single.api.service.dao.UiStateResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class UiStateService {

	@Autowired
	private UiStateResourceService resourceService;

	public void persistTaskPanelState(final String projectKey, final String taskKey, final String username, final String panelId, final String jsonState) throws IOException {
		resourceService.write(getTaskUserPanelPath(projectKey, taskKey, username, panelId), jsonState);
	}

	public String retrieveTaskPanelState(final String projectKey, final String taskKey, final String username, final String panelId) {
		try {
			return resourceService.read(getTaskUserPanelPath(projectKey, taskKey, username, panelId));
		} catch (AmazonS3Exception e) {
			throw new ResourceNotFoundException("ui-state", panelId);
		}
	}

	public String retrieveTaskPanelStateWithoutThrowingResourceNotFoundException(final String projectKey, final String taskKey, final String username, final String panelId) {
		try {
			return resourceService.read(getTaskUserPanelPath(projectKey, taskKey, username, panelId));
		} catch (AmazonS3Exception e) {
			return null;
		}
	}

	public void persistPanelState(final String username, final String panelId, final String jsonState) throws IOException {
		resourceService.write(getUserPanelPath(username, panelId), jsonState);
	}

	public String retrievePanelState(final String username, final String panelId) {
		try {
			return resourceService.read(getUserPanelPath(username, panelId));
		} catch (AmazonS3Exception e) {
			throw new ResourceNotFoundException("ui-state", panelId);
		}
	}
	
	private String getTaskUserPath(final String projectKey, final String taskKey, final String username) {
		return projectKey + "/" + taskKey + "/user/" + username + "/ui-panel/";
	}

	private String getTaskUserPanelPath(final String projectKey, final String taskKey, final String username, final String panelId) {
		return getTaskUserPath(projectKey, taskKey, username) + panelId + ".json";
	}

	private String getUserPanelPath(final String username, final String panelId) {
		return "/user/" + username + "/ui-panel/" + panelId + ".json";
	}

	public void deleteTaskPanelState(final String projectKey, final String taskKey, final String username, final String panelId) throws IOException {
		resourceService.delete(getTaskUserPanelPath(projectKey, taskKey, username, panelId));
	}

	public void deletePanelState(final String username, final String panelId) throws IOException {
		resourceService.delete(getUserPanelPath(username, panelId));
	}

	public void transferTask(final String projectKey, final String taskKey, final TaskTransferRequest taskTransferRequest) throws BusinessServiceException {
		try {
			resourceService.move(getTaskUserPath(projectKey, taskKey, taskTransferRequest.getCurrentUser()),
								 getTaskUserPath(projectKey, taskKey, taskTransferRequest.getNewUser()));
		} catch (AmazonS3Exception e) {
			throw new BusinessServiceException(e);
		}
	}
}
