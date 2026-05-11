package org.ihtsdo.authoringservices.service.impl;

import org.ihtsdo.authoringservices.domain.*;
import org.ihtsdo.authoringservices.service.TaskService;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.CodeSystem;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DefaultTaskServiceImpl implements TaskService {

    private static final String TASK_NOT_FOUND_MSG = "Task not found ";

    @Override
    public boolean exists(String taskKey) {
        throw new ResourceNotFoundException(TASK_NOT_FOUND_MSG + taskKey);
    }

    @Override
    public AuthoringMain retrieveMain() {
        throw new UnsupportedOperationException("Retrieving MAIN is not supported");
    }

    @Override
    public AuthoringTask createTask(String projectKey, String username, AuthoringTaskCreateRequest taskCreateRequest, TaskType type) throws BusinessServiceException {
        throw new ResourceNotFoundException("Project with key " + projectKey + " not found");
    }

    @Override
    public AuthoringTask retrieveTask(String projectKey, String taskKey, Boolean lightweight, boolean skipTaskMigration) throws BusinessServiceException {
        throw new ResourceNotFoundException(TASK_NOT_FOUND_MSG + taskKey);
    }

    @Override
    public AuthoringTask updateTask(String projectKey, String taskKey, AuthoringTaskUpdateRequest taskUpdateRequest) throws BusinessServiceException {
        throw new ResourceNotFoundException(TASK_NOT_FOUND_MSG + taskKey);
    }

    @Override
    public Integer getLatestTaskNumberForProject(String projectKey) {
        throw new ResourceNotFoundException("Project with key " + projectKey + " not found");
    }

    @Override
    public void deleteTasks(Set<String> taskKeys) {
        // Do nothing
    }

    @Override
    public List<AuthoringTask> getTasksByStatus(String projectKey, TaskStatus taskStatus) throws BusinessServiceException {
        return Collections.emptyList();
    }

    @Override
    public List<AuthoringTask> listTasksForProject(String projectKey, Boolean lightweight) throws BusinessServiceException {
        return Collections.emptyList();
    }

    @Override
    public List<AuthoringTask> listMyTasks(List<CodeSystem> codeSystems, String username, String excludePromoted) throws BusinessServiceException {
        return Collections.emptyList();
    }

    @Override
    public List<AuthoringTask> listMyOrUnassignedReviewTasks(List<CodeSystem> codeSystems, String excludePromoted) throws BusinessServiceException {
        return Collections.emptyList();
    }

    @Override
    public List<AuthoringTask> searchTasks(String criteria, Set<String> projectKeys, Set<String> statuses, String author, Long createdDateFrom, Long createdDateTo, Boolean lightweight) throws BusinessServiceException {
        return Collections.emptyList();
    }

    @Override
    public void addCommentLogErrors(String projectKey, String taskKey, String commentString) {
        // Do nothing
    }

    @Override
    public User getUser(String username) throws BusinessServiceException {
        throw new UnsupportedOperationException("Getting user is not supported");
    }

    @Override
    public void conditionalStateTransition(String projectKey, String taskKey, TaskStatus requiredState, TaskStatus newState) throws BusinessServiceException {
        // Do nothing
    }

    @Override
    public void stateTransition(String projectKey, String taskKey, TaskStatus newState) throws BusinessServiceException {
        // Do nothing
    }

    @Override
    public void stateTransition(List<AuthoringTask> issues, TaskStatus newState, String projectKey) throws BusinessServiceException {
        // Do nothing
    }

    @Override
    public List<TaskAttachment> getTaskAttachments(String projectKey, String taskKey) throws BusinessServiceException {
        return Collections.emptyList();
    }

    @Override
    public void getCrsJiraAttachment(String issueLinkKey, List<TaskAttachment> attachments, String issueKey) throws BusinessServiceException {
        // Do nothing
    }

    @Override
    public void leaveCommentForTask(String projectKey, String taskKey, String comment) throws BusinessServiceException {
        // Do nothing
    }

    @Override
    public void removeCrsTaskForGivenRequestJiraKey(String issueKey, String linkId) throws BusinessServiceException {
        // Do nothing
    }

    @Override
    public void removeCrsTaskForGivenRequestId(String projectKey, String taskKey, String crsId) throws BusinessServiceException {
        // Do nothing
    }
}
