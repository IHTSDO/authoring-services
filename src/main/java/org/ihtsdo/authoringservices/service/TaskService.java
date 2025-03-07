package org.ihtsdo.authoringservices.service;

import org.ihtsdo.authoringservices.domain.*;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;

import java.util.List;
import java.util.Set;

public interface TaskService {

    boolean isUseNew(String taskKey);

    AuthoringMain retrieveMain() throws BusinessServiceException;

    AuthoringTask createTask(String projectKey, String username, AuthoringTaskCreateRequest taskCreateRequest, TaskType type) throws BusinessServiceException;

    AuthoringTask retrieveTask(String projectKey, String taskKey, Boolean lightweight, boolean skipTaskMigration) throws BusinessServiceException;

    AuthoringTask updateTask(String projectKey, String taskKey, AuthoringTaskUpdateRequest taskUpdateRequest) throws BusinessServiceException;

    Integer getLatestTaskNumberForProject(String projectKey);

    void deleteTask(String taskKey) throws BusinessServiceException;

    void deleteTasks(Set<String> taskKeys) throws BusinessServiceException;

    List<AuthoringTask> getTasksByStatus(String projectKey, TaskStatus taskStatus) throws BusinessServiceException;

    List<AuthoringTask> listTasksForProject(String projectKey, Boolean lightweight) throws BusinessServiceException;

    List<AuthoringTask> listMyTasks(String username, String excludePromoted, TaskType type) throws BusinessServiceException;

    List<AuthoringTask> listMyOrUnassignedReviewTasks(String excludePromoted) throws BusinessServiceException;

    List<AuthoringTask> searchTasks(String criteria, Boolean lightweight, TaskType type) throws BusinessServiceException;

    void addCommentLogErrors(String projectKey, String taskKey, String commentString);

    User getUser(String username) throws BusinessServiceException;

    void conditionalStateTransition(String projectKey, String taskKey, TaskStatus requiredState, TaskStatus newState) throws BusinessServiceException;

    void stateTransition(String projectKey, String taskKey, TaskStatus newState) throws BusinessServiceException;

    void stateTransition(List<AuthoringTask> issues, TaskStatus newState, String projectKey) throws BusinessServiceException;

    List<TaskAttachment> getTaskAttachments(String projectKey, String taskKey) throws BusinessServiceException;

    void getCrsJiraAttachment(String issueLinkKey, List<TaskAttachment> attachments, String issueKey) throws BusinessServiceException;

    void leaveCommentForTask(String projectKey, String taskKey, String comment) throws BusinessServiceException;

    void removeCrsTaskForGivenRequestJiraKey(String issueKey, String linkId) throws BusinessServiceException;

    void removeCrsTaskForGivenRequestId(String projectKey, String taskKey, String crsId) throws BusinessServiceException;
}
