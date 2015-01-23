/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.service;

import java.util.List;

import com.b2international.snowowl.rest.domain.task.ITask;
import com.b2international.snowowl.rest.domain.task.ITaskInput;
import com.b2international.snowowl.rest.exception.codesystem.CodeSystemNotFoundException;
import com.b2international.snowowl.rest.exception.codesystem.CodeSystemVersionNotFoundException;
import com.b2international.snowowl.rest.exception.task.TaskNotFoundException;
import com.b2international.snowowl.rest.exception.task.TaskPromotionException;
import com.b2international.snowowl.rest.exception.task.TaskSynchronizationConflictException;
import com.b2international.snowowl.rest.exception.task.TaskSynchronizationException;

/**
 * Groups methods related to task-based editing of terminology content.
 * <p>
 * The following operations are supported:
 * <ul>
 * <li>{@link #getAllTasks(String, String, Boolean) <em>Get all tasks for a code system version</em>}
 * <li>{@link #getTaskByName(String, String, String) <em>Get single task by task identifier</em>}
 * <li>{@link #createTask(String, String, String, ITaskInput, String) <em>Create new task</em>}
 * <li>{@link #synchronizeTask(String, String, String, String) <em>Synchronize task with changes on parent</em>}
 * <li>{@link #promoteTask(String, String, String, String) <em>Promote task to parent and close</em>}
 * </ul>
 * <p>
 * The service uses opaque task identifiers which may come from external workflow/issue management systems like 
 * JIRA or Bugzilla.
 * 
 * @author Andras Peteri
 */
public interface ITaskService {

	/**
	 * Retrieves all tasks for the specified code system version.
	 * 
	 * @param shortName the code system short name to look for, eg. "{@code SNOMEDCT}" (may not be {@code null})
	 * @param version the code system version identifier to look for, eg. "{@code 2014-07-31}" (may not be {@code null})
	 * @param includePromoted {@code true} if promoted and closed tasks should be added to the list, {@code false} otherwise
	 * @return a list of tasks created for editing the specified code system version, ordered by task identifier (never {@code null})
	 * @throws CodeSystemNotFoundException if a code system with the given short name is not registered
	 * @throws CodeSystemVersionNotFoundException if a code system version for the code system with the given identifier
	 * is not registered
	 */
	List<ITask> getAllTasks(String shortName, String version, boolean includePromoted);

	/**
	 * Retrieves a single task by name, if it exists.
	 * 
	 * @param shortName the code system short name to look for, eg. "{@code SNOMEDCT}" (may not be {@code null})
	 * @param version the code system version identifier to look for, eg. "{@code 2014-07-31}" (may not be {@code null})
	 * @param taskId the identifier of the task to look for, eg. "{@code 1432}" (may not be {@code null})
	 * @return the task for the specified code system version, with the given identifier
	 * @throws CodeSystemNotFoundException if a code system with the given short name is not registered
	 * @throws CodeSystemVersionNotFoundException if a code system version for the code system with the given identifier
	 * is not registered
	 * @throws TaskNotFoundException if the task identifier does not correspond to a task for the given code system version
	 */
	ITask getTaskByName(String shortName, String version, String taskId);

	/**
	 * Creates a new editing task for a code system.
	 * 
	 * @param shortName the code system short name to look for, eg. "{@code SNOMEDCT}" (may not be {@code null})
	 * @param version the code system version identifier to look for, eg. "{@code 2014-07-31}" (may not be {@code null})
	 * @param taskId the identifier of the task to create, eg. "{@code 1432}" (may not be {@code null})
	 * @param input additional information of the task to be created (may not be {@code null})
	 * @param userId the identifier of the user requesting task creation (may not be {@code null})
	 * @return the created task
	 * @throws CodeSystemNotFoundException if a code system with the given short name is not registered
	 * @throws CodeSystemVersionNotFoundException if a code system version for the code system with the given identifier
	 * @throws DuplicateTaskException if the task identifier is already assigned
	 * @throws TaskCreationException if any other error occurs while creating the task
	 */
	ITask createTask(String shortName, String version, String taskId, ITaskInput input, String userId);

	/**
	 * Makes changes that happened on the task's parent version available on the task, without losing changes that
	 * happened on the task itself.
	 * 
	 * @param shortName the code system short name to look for, eg. "{@code SNOMEDCT}" (may not be {@code null})
	 * @param version the code system version identifier to look for, eg. "{@code 2014-07-31}" (may not be {@code null})
	 * @param taskId the identifier of the task to synchronize, eg. "{@code 1432}" (may not be {@code null})
	 * @param userId the identifier of the user requesting task synchronization (may not be {@code null})
	 * @throws CodeSystemNotFoundException if a code system with the given short name is not registered
	 * @throws CodeSystemVersionNotFoundException if a code system version for the code system with the given identifier
	 * is not registered
	 * @throws TaskNotFoundException if the task identifier does not correspond to a task for the given code system version
	 * @throws TaskSynchronizationConflictException if synchronization can not be done as conflicting changes were made
	 * on this branch
	 * @throws TaskSynchronizationException if any other error occurs while synchronizing the task
	 */
	void synchronizeTask(String shortName, String version, String taskId, String userId);

	/**
	 * Makes changes that happened on the task permanent on the parent version. The task to promote must be
	 * synchronized.
	 * 
	 * @param shortName the code system short name to look for, eg. "{@code SNOMEDCT}" (may not be {@code null})
	 * @param version the code system version identifier to look for, eg. "{@code 2014-07-31}" (may not be {@code null})
	 * @param taskId the identifier of the task to promote, eg. "{@code 1432}" (may not be {@code null})
	 * @param userId the identifier of the user requesting task promotion (may not be {@code null})
	 * @throws CodeSystemNotFoundException if a code system with the given short name is not registered
	 * @throws CodeSystemVersionNotFoundException if a code system version for the code system with the given identifier
	 * is not registered
	 * @throws TaskNotFoundException if the task identifier does not correspond to a task for the given code system version
	 * @throws TaskSynchronizationException if the task is not synchronized to the parent version
	 * @throws TaskPromotionException if any other error occurs while promoting the task
	 */
	void promoteTask(String shortName, String version, String taskId, String userId);
}
