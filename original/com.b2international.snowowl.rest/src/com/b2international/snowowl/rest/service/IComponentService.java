/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.service;

import com.b2international.snowowl.rest.domain.IComponent;
import com.b2international.snowowl.rest.domain.IComponentInput;
import com.b2international.snowowl.rest.domain.IComponentRef;
import com.b2international.snowowl.rest.exception.ComponentNotFoundException;
import com.b2international.snowowl.rest.exception.codesystem.CodeSystemNotFoundException;
import com.b2international.snowowl.rest.exception.codesystem.CodeSystemVersionNotFoundException;
import com.b2international.snowowl.rest.exception.task.TaskNotFoundException;

/**
 * Provides methods for creating, retrieving details and updating a single component type.
 * <p>
 * The following operations are supported:
 * <ul>
 * <li>{@link #create(C, String, String) <em>Create new component</em>}
 * <li>{@link #create(IComponentRef, C, String) <em>Create new component with external identifier</em>}
 * <li>{@link #read(IComponentRef) <em>Read existing component</em>}
 * <li>{@link #update(IComponentRef, U, String, String) <em>Update existing component</em>}
 * <li>{@link #delete(IComponentRef, String, String) <em>Delete existing component</em>}
 * </ul>
 * 
 * @param <C> the type of the model for creating a new component instance
 * @param <R> the type of the model for retrieving component details
 * @param <U> the type of the model for updating an existing component instance
 * 
 * @author Andras Peteri
 */
public interface IComponentService<C extends IComponentInput, R extends IComponent, U> {

	/**
	 * Creates a new component with the specified arguments.
	 * 
	 * @param input the properties of the component to create (may not be {@code null})
	 * @param userId the identifier of the user who creates the component (may not be {@code null})
	 * @param commitComment TODO
	 * @return the created component's details
	 * @throws CodeSystemNotFoundException if a code system with the given short name is not registered
	 * @throws CodeSystemVersionNotFoundException if a code system version for the code system with the given identifier
	 * is not registered
	 * @throws TaskNotFoundException if the task identifier does not correspond to a task for the given code system version
	 * @throws ComponentValidationException if the component input fails validation
	 * @throws ComponentCreationException if creating the component fails for any other reason
	 */
	R create(C input, String userId, String commitComment);

	/**
	 * Retrieves component details identified by the given parameters, if it exists.
	 * 
	 * @param ref the component reference pointing to the component to read (may not be {@code null})
	 * @return the component details
	 * @throws CodeSystemNotFoundException if a code system with the given short name is not registered
	 * @throws CodeSystemVersionNotFoundException if a code system version for the code system with the given identifier
	 * is not registered
	 * @throws TaskNotFoundException if the task identifier does not correspond to a task for the given code system version
	 * @throws ComponentNotFoundException if the component identifier does not match any component on the given task
	 */
	R read(IComponentRef ref);

	/**
	 * Updates an existing component using the specified update model, if it exists.
	 * 
	 * @param ref the component reference pointing to the component to update (may not be {@code null})
	 * @param update the properties of the component to update (may not be {@code null})
	 * @param userId the identifier of the user who updates the component (may not be {@code null})
	 * @param commitComment TODO
	 * @return the component's details after updating
	 * @throws CodeSystemNotFoundException if a code system with the given short name is not registered
	 * @throws CodeSystemVersionNotFoundException if a code system version for the code system with the given identifier
	 * is not registered
	 * @throws TaskNotFoundException if the task identifier does not correspond to a task for the given code system version
	 * @throws ComponentNotFoundException if the component identifier does not match any component on the given task
	 * @throws ComponentValidationException if the update instance fails validation
	 * @throws ComponentUpdateException if updating the component fails for any other reason
	 */
	R update(IComponentRef ref, U update, String userId, String commitComment);

	/**
	 * Permanently deletes the specified component.
	 * 
	 * @param ref the component reference pointing to the component to update (may not be {@code null})
	 * @param userId the identifier of the user who deletes the component (may not be {@code null})
	 * @param commitComment TODO
	 * @return the component's details after updating
	 * @throws CodeSystemNotFoundException if a code system with the given short name is not registered
	 * @throws CodeSystemVersionNotFoundException if a code system version for the code system with the given identifier
	 * is not registered
	 * @throws TaskNotFoundException if the task identifier does not correspond to a task for the given code system version
	 * @throws ComponentNotFoundException if the component identifier does not match any component on the given task
	 * @throws ComponentDeleteException if deleting the component fails for any other reason
	 */
	void delete(IComponentRef ref, String userId, String commitComment);
}
