package org.ihtsdo.snowowl.authoring.single.api.service;

import com.google.common.collect.Lists;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.snowowl.SnowOwlRestClient;
import org.ihtsdo.otf.rest.client.snowowl.SnowOwlRestClientFactory;
import org.ihtsdo.otf.rest.client.snowowl.pojo.Branch;
import org.ihtsdo.otf.rest.client.snowowl.pojo.Merge;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.ihtsdo.otf.rest.client.snowowl.pojo.Merge.Status.IN_PROGRESS;
import static org.ihtsdo.otf.rest.client.snowowl.pojo.Merge.Status.SCHEDULED;

public class BranchService {
	
	@Autowired
	private SnowOwlRestClientFactory snowOwlRestClientFactory;

	public String getBranchState(String branchPath) throws ServiceException {
		try {
			return snowOwlRestClientFactory.getClient().getBranch(branchPath).getState();
		} catch (RestClientException e) {
			throw new ServiceException("Failed to fetch branch state for branch " + branchPath, e);
		}
	}

	public Branch getBranchOrNull(String branchPath) throws ServiceException {
		try {
			return snowOwlRestClientFactory.getClient().getBranch(branchPath);
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
			snowOwlRestClientFactory.getClient().createBranch(branchPath);
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

	public Merge mergeBranchSync(String sourcePath, String targetPath, String reviewId) throws BusinessServiceException {
		Logger logger = LoggerFactory.getLogger(getClass());
		logger.info("Attempting branch merge from '{}' to '{}'", sourcePath, targetPath);
		try {
			SnowOwlRestClient client = snowOwlRestClientFactory.getClient();
			String mergeId = client.startMerge(sourcePath, targetPath, reviewId);
			Merge merge;
			int sleepSeconds = 4;
			int totalWait = 0;
			int maxTotalWait = 60 * 60;
			try {
				do {
					Thread.sleep(1000 * sleepSeconds);
					totalWait += sleepSeconds;
					merge = client.getMerge(mergeId);
					if (sleepSeconds < 10) {
						sleepSeconds+=2;
					}
				} while (totalWait < maxTotalWait && (merge.getStatus() == SCHEDULED || merge.getStatus() == IN_PROGRESS));

				logger.info("Branch merge from '{}' to '{}' end status is {} {}", sourcePath, targetPath, merge.getStatus(), merge.getApiError() == null ? "" : merge.getApiError());
				return merge;

			} catch (InterruptedException | RestClientException e) {
				throw new BusinessServiceException("Failed to fetch merge status.", e);
			}
		} catch (RestClientException e) {
			throw new BusinessServiceException("Failed to start merge.", e);
		}
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
