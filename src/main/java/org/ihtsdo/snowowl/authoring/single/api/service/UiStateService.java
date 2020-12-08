package org.ihtsdo.snowowl.authoring.single.api.service;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.TaskTransferRequest;
import org.ihtsdo.snowowl.authoring.single.api.service.dao.UiStateResourceService;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

@Service
public class UiStateService {

	@Autowired
	private UiStateResourceService resourceService;

	public void persistTaskPanelState(final String projectKey, final String taskKey, final String username, final String panelId, final String jsonState) throws IOException {
		// Check to make sure that the state is valid JSON.
		new JSONObject(jsonState);

		resourceService.write(getTaskUserPanelPath(projectKey, taskKey, username, panelId), jsonState);
	}

	public String retrieveTaskPanelState(final String projectKey, final String taskKey, final String username, final String panelId) throws IOException {
		try {
			return resourceService.read(getTaskUserPanelPath(projectKey, taskKey, username, panelId));
		} catch (NoSuchFileException e) {
			throw new ResourceNotFoundException("ui-state", panelId);
		}
	}

	public String retrieveTaskPanelStateWithoutThrowingResourceNotFoundException(final String projectKey, final String taskKey, final String username, final String panelId)
			throws IOException {
		try {
			return resourceService.read(getTaskUserPanelPath(projectKey, taskKey, username, panelId));
		} catch (NoSuchFileException e) {
			return null;
		}
	}

	public void persistPanelState(final String username, final String panelId, final String jsonState) throws IOException {
		// Check to make sure that the state is valid JSON.
		new JSONObject(jsonState);

		resourceService.write(getUserPanelPath(username, panelId), jsonState);
	}

	public String retrievePanelState(final String username, final String panelId) throws IOException {
		try {
			return resourceService.read(getUserPanelPath(username, panelId));
		} catch (NoSuchFileException e) {
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
		return "user/" + username + "/ui-panel/" + panelId + ".json";
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
		} catch (IOException e) {
			throw new BusinessServiceException(e);
		}
	}
}
