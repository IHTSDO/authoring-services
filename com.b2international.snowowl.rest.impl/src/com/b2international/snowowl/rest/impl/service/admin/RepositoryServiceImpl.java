/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.impl.service.admin;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.text.MessageFormat;
import java.util.List;
import java.util.Set;

import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.core.api.index.IIndexEntry;
import com.b2international.snowowl.core.api.index.IIndexUpdater;
import com.b2international.snowowl.core.users.SpecialUserStore;
import com.b2international.snowowl.datastore.BranchPathUtils;
import com.b2international.snowowl.datastore.CodeSystemService;
import com.b2international.snowowl.datastore.ICodeSystemVersion;
import com.b2international.snowowl.datastore.oplock.IOperationLockTarget;
import com.b2international.snowowl.datastore.oplock.OperationLockException;
import com.b2international.snowowl.datastore.oplock.impl.AllRepositoriesLockTarget;
import com.b2international.snowowl.datastore.oplock.impl.DatastoreLockContext;
import com.b2international.snowowl.datastore.oplock.impl.DatastoreLockContextDescriptions;
import com.b2international.snowowl.datastore.oplock.impl.DatastoreOperationLockException;
import com.b2international.snowowl.datastore.oplock.impl.IDatastoreOperationLockManager;
import com.b2international.snowowl.datastore.oplock.impl.SingleRepositoryLockTarget;
import com.b2international.snowowl.datastore.server.ICDORepositoryManager;
import com.b2international.snowowl.datastore.server.index.IndexServerServiceManager;
import com.b2international.snowowl.rest.exception.admin.LockConflictException;
import com.b2international.snowowl.rest.exception.admin.LockException;
import com.b2international.snowowl.rest.exception.admin.RepositoryNotFoundException;
import com.b2international.snowowl.rest.exception.admin.RepositoryVersionNotFoundException;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;

/**
 * @author apeteri
 */
public class RepositoryServiceImpl implements InternalRepositoryService {

	private static IDatastoreOperationLockManager getLockManager() {
		return ApplicationContext.getServiceForClass(IDatastoreOperationLockManager.class);
	}
	
	private static final Function<ICodeSystemVersion, String> EXTRACT_VERSION_ID = new Function<ICodeSystemVersion, String>() { 
		@Override public String apply(final ICodeSystemVersion input) {
			return input.getVersionId();
		}
	};

	private static ICDORepositoryManager getRepositoryManager() {
		return ApplicationContext.getServiceForClass(ICDORepositoryManager.class);
	}

	private static CodeSystemService getCodeSystemService() {
		return ApplicationContext.getServiceForClass(CodeSystemService.class);
	}

	protected void checkValidRepositoryUuid(final String repositoryUuid) {
		checkNotNull(repositoryUuid, "Repository identifier may not be null.");

		if (!getRepositoryUuids().contains(repositoryUuid)) {
			throw new RepositoryNotFoundException(repositoryUuid);
		}
	}

	@Override
	public void checkValidRepositoryAndVersionId(final String repositoryUuid, final String repositoryVersionId) {
		checkNotNull(repositoryVersionId, "Repository version identifier may not be null.");

		if (!getRepositoryVersionIds(repositoryUuid).contains(repositoryVersionId)) {
			throw new RepositoryVersionNotFoundException(repositoryVersionId);
		}
	}

	@Override
	public List<String> getRepositoryUuids() {
		final Set<String> uuids = getRepositoryManager().uuidKeySet();
		return Ordering.natural().immutableSortedCopy(uuids);
	}

	@Override
	public List<String> getRepositoryVersionIds(final String repositoryUuid) {
		checkValidRepositoryUuid(repositoryUuid);

		final List<ICodeSystemVersion> allTagsWithHead = getCodeSystemService().getAllTagsWithHead(repositoryUuid);
		final List<String> versionStrings = Lists.transform(allTagsWithHead, EXTRACT_VERSION_ID);
		return ImmutableList.copyOf(versionStrings);
	}

	@Override
	public void lockGlobal(final int timeoutMillis) {
		checkValidTimeout(timeoutMillis);

		final DatastoreLockContext context = new DatastoreLockContext(SpecialUserStore.SYSTEM_USER_NAME, 
				DatastoreLockContextDescriptions.CREATE_BACKUP);

		final IOperationLockTarget target = AllRepositoriesLockTarget.INSTANCE;
		doLock(timeoutMillis, context, target);
	}

	@Override
	public void unlockGlobal() {
		final DatastoreLockContext context = new DatastoreLockContext(SpecialUserStore.SYSTEM_USER_NAME, 
				DatastoreLockContextDescriptions.CREATE_BACKUP);

		final IOperationLockTarget target = AllRepositoriesLockTarget.INSTANCE;
		doUnlock(context, target);
	}

	@Override
	public void lockRepository(final String repositoryUuid, final int timeoutMillis) {
		checkValidRepositoryUuid(repositoryUuid);
		checkValidTimeout(timeoutMillis);

		final DatastoreLockContext context = new DatastoreLockContext(SpecialUserStore.SYSTEM_USER_NAME, 
				DatastoreLockContextDescriptions.CREATE_REPOSITORY_BACKUP,
				DatastoreLockContextDescriptions.CREATE_BACKUP);

		final IOperationLockTarget target = new SingleRepositoryLockTarget(repositoryUuid);
		doLock(timeoutMillis, context, target);
	}

	@Override
	public void unlockRepository(final String repositoryUuid) {
		checkValidRepositoryUuid(repositoryUuid);

		final DatastoreLockContext context = new DatastoreLockContext(SpecialUserStore.SYSTEM_USER_NAME, 
				DatastoreLockContextDescriptions.CREATE_REPOSITORY_BACKUP,
				DatastoreLockContextDescriptions.CREATE_BACKUP);

		final IOperationLockTarget target = new SingleRepositoryLockTarget(repositoryUuid);
		doUnlock(context, target);
	}

	@Override
	public List<String> getRepositoryVersionIndexFiles(final String repositoryUuid, final String repositoryVersionId) {
		checkValidRepositoryAndVersionId(repositoryUuid, repositoryVersionId);

		final IIndexUpdater<IIndexEntry> updater = IndexServerServiceManager.INSTANCE.getIndexService(repositoryUuid);
		final IBranchPath versionPath = BranchPathUtils.createVersionPath(repositoryVersionId);
		final List<String> fileList =  updater.listFiles(versionPath);
		return ImmutableList.copyOf(fileList);
	}

	private void checkValidTimeout(final int timeoutMillis) {
		checkArgument(timeoutMillis >= 0, "Timeout in milliseconds may not be negative.");
	}

	private void doLock(final int timeoutMillis, final DatastoreLockContext context, final IOperationLockTarget target) {
		try {
			getLockManager().lock(context, timeoutMillis, target);
		} catch (final DatastoreOperationLockException e) {

			final DatastoreLockContext conflictingContext = e.getContext(target);
			throw new LockConflictException(MessageFormat.format("Failed to acquire lock for all repositories because {0} is {1}.", 
					conflictingContext.getUserId(), 
					conflictingContext.getDescription()));

		} catch (final OperationLockException | InterruptedException e) {
			throw new LockException(e.getMessage());
		}
	}

	private void doUnlock(final DatastoreLockContext context, final IOperationLockTarget target) {
		try {
			getLockManager().unlock(context, target);
		} catch (final OperationLockException e) {
			throw new LockException(e.getMessage());
		}
	}
}
