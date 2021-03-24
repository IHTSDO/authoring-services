package org.ihtsdo.authoringservices.service;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;
import org.ihtsdo.authoringservices.domain.TaskTransferRequest;
import org.ihtsdo.authoringservices.service.dao.UiStateResourceService;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

@Service
public class UiStateService {

	@Autowired
	private UiStateResourceService resourceService;

	public void persistTaskPanelState(final String projectKey, final String taskKey, final String username, final String panelId, final String jsonState) throws IOException {
		if (isValidJson(jsonState)) {
			resourceService.write(getTaskUserPanelPath(projectKey, taskKey, username, panelId), jsonState);
		}
	}

	private boolean isValidJson(final String jsonState) {
		final Object data = new JSONTokener(jsonState).nextValue();
		if (data instanceof JSONObject || data instanceof JSONArray) {
			return true;
		}
		throw new JSONException("JSON panel state is malformed.");
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
		if (isValidJson(jsonState)) {
			resourceService.write(getUserPanelPath(username, panelId), jsonState);
		}
	}

	public String retrievePanelState(final String username, final String panelId) throws IOException {
		try {
			return resourceService.read(getUserPanelPath(username, panelId));
		} catch (NoSuchFileException e) {
			throw new ResourceNotFoundException("ui-state", panelId);
		}
	}

	public String retrievePanelStateWithoutThrowingResourceNotFoundException(final String username, final String panelId) throws IOException {
		try {
			return resourceService.read(getUserPanelPath(username, panelId));
		} catch (NoSuchFileException e) {
			return null;
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
