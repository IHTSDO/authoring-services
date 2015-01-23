/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.service.admin;

import java.util.List;

import com.b2international.snowowl.rest.exception.admin.LockException;
import com.b2international.snowowl.rest.exception.admin.RepositoryNotFoundException;
import com.b2international.snowowl.rest.exception.admin.RepositoryVersionNotFoundException;

/**
 * An interface definition for the Repository Service.
 * <p>
 * The following operations are supported:
 * <ul>
 * <li>{@link #getRepositoryUuids() <em>Retrieve all repository identifiers</em>}
 * <li>{@link #lockGlobal(int) <em>Lock all repositories</em>}
 * <li>{@link #unlockGlobal() <em>Unlock all repositories</em>}
 * <li>{@link #lockRepository(String, int) <em>Lock single repository</em>}
 * <li>{@link #unlockRepository(String) <em>Unlock single repository</em>}
 * <li>{@link #getRepositoryVersionIds(String) <em>Retrieve all version identifiers for a repository</em>}
 * <li>{@link #getRepositoryVersionIndexFiles(String, String) <em>Retrieve all repository version index file paths</em>}
 * </ul>
 * 
 * @author Andras Peteri
 */
public interface IRepositoryService {

	/**
	 * Retrieves the unique identifier of each running repository that stores terminology content.
	 * 
	 * @return a list of repository identifiers, in alphabetical order (never {@code null})
	 */
	List<String> getRepositoryUuids();

	/**
	 * Places a global lock, which prevents other users from making changes to any of the repositories while a backup is
	 * created. The call may block up to the specified timeout to acquire the lock; if {@code timeoutMillis} is set to
	 * {@code 0}, it returns immediately.
	 * 
	 * @param timeoutMillis lock timeout in milliseconds (may not be negative)
	 * @throws LockException if the global lock could not be acquired for any reason (including cases when a conflicting
	 * lock is already held by someone else)
	 */
	void lockGlobal(int timeoutMillis);

	/**
	 * Releases a previously acquired global lock.
	 * 
	 * @throws LockException if the global lock could not be released for any reason (including cases when it was not held)
	 */
	void unlockGlobal();

	/**
	 * Places a repository-level lock, which prevents other users from making changes to the repository identified by
	 * {@code repositoryUuid}. The call may wait up to the specified timeout to acquire the lock; if
	 * {@code timeoutMillis} is set to {@code 0}, it returns immediately.
	 * 
	 * @param repositoryUuid a unique identifier pointing to a particular repository (may not be {@code null})
	 * @param timeoutMillis lock timeout in milliseconds (may not be negative)
	 * @throws RepositoryNotFoundException if the specified repository UUID does not correspond to any repository
	 * @throws LockException if the repository lock could not be acquired for any reason (including cases when a
	 * conflicting lock is already held by someone else)
	 */
	void lockRepository(String repositoryUuid, int timeoutMillis);

	/**
	 * Releases a previously acquired repository-level lock on the repository identified by {@code repositoryUuid}.
	 * 
	 * @param repositoryUuid a unique identifier pointing to a particular repository (may not be {@code null})
	 * @throws RepositoryNotFoundException if the specified repository UUID does not correspond to any repository
	 * @throws LockException if the repository lock could not be released for any reason (including cases when it was
	 * not held)
	 */
	void unlockRepository(String repositoryUuid);

	/**
	 * Retrieves all version identifiers for the specified repository.
	 * 
	 * @param repositoryUuid a unique identifier pointing to a particular repository (may not be {@code null})
	 * @return a list of repository version identifiers, in alphabetical order (never {@code null})
	 * @throws RepositoryNotFoundException if the specified repository UUID does not correspond to any repository
	 */
	List<String> getRepositoryVersionIds(String repositoryUuid);

	/**
	 * Retrieves the relative path of all files that make up the index of the specified repository and version.
	 * 
	 * @param repositoryUuid a unique identifier pointing to a particular repository (may not be {@code null})
	 * @param repositoryVersionId the identifier of a repository version, as returned by {@link #getRepositoryVersionIds(String)} (may not be {@code null})
	 * @return a list of relative paths to files which make up the index of the given version, in alphabetical order (never {@code null})
	 * @throws RepositoryNotFoundException if the specified repository UUID does not correspond to any repository
	 * @throws RepositoryVersionNotFoundException if the specified version identifier does not correspond to a version
	 * in the repository
	 */
	List<String> getRepositoryVersionIndexFiles(String repositoryUuid, String repositoryVersionId);
}
