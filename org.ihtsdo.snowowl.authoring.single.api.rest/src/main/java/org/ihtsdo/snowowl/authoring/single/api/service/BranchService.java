package org.ihtsdo.snowowl.authoring.single.api.service;

import com.google.common.collect.Lists;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.snowowl.Branch;
import org.ihtsdo.otf.rest.client.snowowl.SnowOwlRestClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BranchService {
	
	@Autowired
	private SnowOwlRestClient snowOwlRestClient;

	public String getBranchState(String branchPath) throws ServiceException {
		try {
			return snowOwlRestClient.getBranch(branchPath).getState();
		} catch (RestClientException e) {
			throw new ServiceException("Failed to fetch branch state for branch " + branchPath, e);
		}
	}

	public Branch getBranchOrNull(String branchPath) throws ServiceException {
		try {
			return snowOwlRestClient.getBranch(branchPath);
		} catch (RestClientException e) {
			throw new ServiceException("Failed to fetch branch " + branchPath, e);
		}
	}

	public String getBranchStateOrNull(String branchPath) throws ServiceException {
		final Branch branchOrNull = getBranchOrNull(branchPath);
		return branchOrNull == null ? null : branchOrNull.getState();
	}

	private void createBranch(String branchPath) throws ServiceException {
		try {
			snowOwlRestClient.createBranch(branchPath);
		} catch (RestClientException e) {
			throw new ServiceException("Failed to create branch " + branchPath, e);
		}
	}

	public void createProjectBranchIfNeeded(String branchPath) throws ServiceException {
		if (getBranchOrNull(branchPath) == null) {
			createBranch(branchPath);
		}
	}

	public Map<String, Object> getBranchMetadataIncludeInherited(String path) throws ServiceException {
		Map<String, Object> mergedMetadata = null;
		List<String> stackPaths = getBranchPathStack(path);
		for (String stackPath : stackPaths) {
			final Branch branch = getBranchOrNull(stackPath);
			final Map<String, Object> metadata = branch.getMetadata();
			if (mergedMetadata == null) {
				mergedMetadata = metadata;
			} else {
				// merge metadata
				for (String key : metadata.keySet()) {
					if (!key.equals("lock") || stackPath.equals(path)) { // Only copy lock info from the deepest branch
						mergedMetadata.put(key, metadata.get(key));
					}
				}
			}
		}
		return mergedMetadata;
	}
	
	private List<String> getBranchPathStack(String path) {
		List<String> paths = new ArrayList<>();
		paths.add(path);
		int index;
		while ((index = path.lastIndexOf("/")) != -1) {
			path = path.substring(0, index);
			paths.add(path);
		}
		return Lists.reverse(paths);
	}

}
