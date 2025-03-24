package org.ihtsdo.authoringservices.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.PreDestroy;
import org.ihtsdo.authoringservices.domain.AuthoringProject;
import org.ihtsdo.authoringservices.domain.BranchState;
import org.ihtsdo.authoringservices.domain.ProcessStatus;
import org.ihtsdo.authoringservices.service.factory.ProjectServiceFactory;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.PathHelper;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ApiError;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Merge;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.MergeReviewsResults;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.ihtsdo.otf.rest.client.terminologyserver.pojo.MergeReviewsResults.MergeReviewStatus.CURRENT;
import static org.ihtsdo.otf.rest.client.terminologyserver.pojo.MergeReviewsResults.MergeReviewStatus.PENDING;

@Service
public class RebaseService {
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public static final String UNDERSCORE = "_";

	public enum RebaseStatus {
		SKIPPED("Skipped"),
		REBASING("Rebasing"),
		REBASE_COMPLETE("Rebase Complete"),
		REBASE_ERROR("Rebase Error"),
		REBASE_CONFLICTS("CONFLICTS");

		private final String label;

		RebaseStatus(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}
	}

	@Autowired
	private SnowstormRestClientFactory snowstormRestClientFactory;

	@Autowired
	private ProjectServiceFactory projectServiceFactory;

	@Autowired
	private BranchService branchService;

	private final ExecutorService executorService;

	private final Cache<String, ProcessStatus> rebaseStatusCache;

	public RebaseService() {
		this.rebaseStatusCache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.DAYS).build();
		executorService = Executors.newCachedThreadPool();
	}

	public void doTaskRebase(String projectKey, String taskKey) {
		final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		executorService.submit(() -> {
			SecurityContextHolder.getContext().setAuthentication(authentication);
			try {
				updateRebaseStatus(RebaseStatus.REBASING, null, parseKey(projectKey, taskKey));
				String taskBranchPath = branchService.getTaskBranchPathUsingCache(projectKey, taskKey);
				mergeBranch(PathHelper.getParentPath(taskBranchPath), taskBranchPath, null, parseKey(projectKey, taskKey));
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
				updateRebaseStatus(RebaseStatus.REBASE_ERROR, e.getMessage(), parseKey(projectKey, taskKey));
			}
		});
	}
	public void doProjectRebase(String jobId, final AuthoringProject project) throws BusinessServiceException {
		String key = jobId != null ? jobId + UNDERSCORE + project.getKey() : project.getKey();
		if (skipProjectRebaseIfDisabled(jobId, project, key)) return;
		if (skipProjectRebaseIfBranchStateNotValid(jobId, project, key)) return;
		if (skipProjectRebaseIfRunning(jobId, project.getKey(), key)) return;
		final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		executorService.submit(() -> {
			SecurityContextHolder.getContext().setAuthentication(authentication);
			try {
				updateRebaseStatus(RebaseStatus.REBASING, null, key);
				String targetBranch = branchService.getProjectBranchPathUsingCache(project.getKey());
				String sourceBranch = PathHelper.getParentPath(targetBranch);
				if (BranchState.BEHIND.name().equals(project.getBranchState())) {
					mergeBranch(sourceBranch, targetBranch, null, key);
					return;
				}

				SnowstormRestClient client = snowstormRestClientFactory.getClient();
				String mergeReviewsId = client.createBranchMergeReviews(sourceBranch, targetBranch);
				MergeReviewsResults mergeReviewsResults = waitToMergeReviewsComplete(client, mergeReviewsId);
				if (CURRENT.equals(mergeReviewsResults.getStatus())) {
					Set mergeReviewsDetails = client.getMergeReviewsDetails(mergeReviewsId);
					if (mergeReviewsDetails.isEmpty()) {
						mergeBranch(sourceBranch, targetBranch, mergeReviewsId, key);
					} else {
						updateRebaseStatus(RebaseStatus.REBASE_CONFLICTS, null, key);
					}
				} else {
					updateRebaseStatus(RebaseStatus.REBASE_ERROR, "Failed to generate the merge-review.", key);
				}
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
				updateRebaseStatus(RebaseStatus.REBASE_ERROR, e.getMessage(), key);
			}
		});
	}

	private void mergeBranch(String sourceBranch, String targetBranch, String mergeReviewsId, String key) throws BusinessServiceException {
		Merge merge = branchService.mergeBranchSync(sourceBranch, targetBranch, mergeReviewsId);
		if (merge.getStatus() == Merge.Status.COMPLETED) {
			updateRebaseStatus(RebaseStatus.REBASE_COMPLETE, null, key);
		} else if (merge.getStatus().equals(Merge.Status.CONFLICTS)) {
			processRebaseConflicts(merge, key);
		} else {
			ApiError apiError = merge.getApiError();
			String message = apiError != null ? apiError.getMessage() : null;
			updateRebaseStatus(RebaseStatus.REBASE_ERROR, message, key);
		}
	}

	private MergeReviewsResults waitToMergeReviewsComplete(SnowstormRestClient client, String mergeReviewsId) throws RestClientException {
		int sleepSeconds = 4;
		int totalWait = 0;
		int maxTotalWait = 60 * 60;
		MergeReviewsResults mergeReviewsResults;
		do {
            try {
                Thread.sleep(1000L * sleepSeconds);
            } catch (InterruptedException e) {
				Thread.currentThread().interrupt();
            }
            totalWait += sleepSeconds;
			mergeReviewsResults = client.getMergeReviewsResult(mergeReviewsId);
			if (sleepSeconds < 10) {
				sleepSeconds += 2;
			}
		} while (totalWait < maxTotalWait && mergeReviewsResults.getStatus() == PENDING);

		return mergeReviewsResults;
	}

	public void doProjectRebase(final String jobId, final String projectKey) throws BusinessServiceException {
		AuthoringProject project = retrieveProject(projectKey);
		this.doProjectRebase(jobId, project);
	}

	private boolean skipProjectRebaseIfBranchStateNotValid(String jobId, AuthoringProject project, String key) throws BusinessServiceException {
		if (project.getBranchState() == null || BranchState.UP_TO_DATE.name().equals(project.getBranchState())
				|| BranchState.FORWARD.name().equals(project.getBranchState())) {
			String message = String.format("No rebase needed for project %s with branch state %s", project.getKey(), project.getBranchState());
			if (jobId != null) {
				updateRebaseStatus(RebaseStatus.SKIPPED, message, key);
				return true;
			}
			throw new BusinessServiceException(message);
		}
		return false;
	}

	public ProcessStatus getTaskRebaseStatus(String projectKey, String taskKey) {
		return this.rebaseStatusCache.getIfPresent(parseKey(projectKey, taskKey));
	}

	public ProcessStatus getProjectRebaseStatus(String projectKey) {
		return this.rebaseStatusCache.getIfPresent(projectKey);
	}

	public Map<String, ProcessStatus> getRebaseStatusByJobId(String jobId) {
		Map<String, ProcessStatus> result = new HashMap<>();
		for (Map.Entry<String, ProcessStatus> entry : this.rebaseStatusCache.asMap().entrySet()) {
			String k = entry.getKey();
			if (k.startsWith(jobId)) {
				result.put(k.replace(jobId + UNDERSCORE, ""), entry.getValue());
			}
		}

		return result;
	}

	private boolean skipProjectRebaseIfDisabled(String jobId, AuthoringProject project, String key) throws BusinessServiceException {
		if (Boolean.TRUE.equals(project.isProjectRebaseDisabled()) || Boolean.TRUE.equals(project.isProjectLocked())) {
			String message = "Project rebase is disabled" + (!Boolean.TRUE.equals(project.isProjectRebaseDisabled()) ? " due to project being locked" : "");
			if (jobId != null) {
				if (!Boolean.TRUE.equals(project.isProjectRebaseDisabled())) {
					updateRebaseStatus(RebaseStatus.SKIPPED, message, key);
				}
				return true;
			}
			throw new BusinessServiceException(message);
		}
		return false;
	}

	private boolean skipProjectRebaseIfRunning(String jobId, String projectKey, String key) throws BusinessServiceException {
		for (Map.Entry<String, ProcessStatus> entry : rebaseStatusCache.asMap().entrySet()) {
			String k = entry.getKey();
			if ((k.equals(projectKey) || k.endsWith(UNDERSCORE + projectKey)) && RebaseStatus.REBASING.getLabel().equals(entry.getValue().getStatus())) {
				String message = "Project is being rebased";
				if (jobId != null) {
					updateRebaseStatus(RebaseStatus.SKIPPED, message, key);
					return true;
				}
				throw new BusinessServiceException(message);
			}
		}
		return false;
	}

	private void processRebaseConflicts(Merge merge, String key) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			String jsonInString = mapper.writeValueAsString(merge);
			updateRebaseStatus(RebaseStatus.REBASE_CONFLICTS, jsonInString, key);
		} catch (JsonProcessingException e) {
			logger.error(e.getMessage(), e);
			updateRebaseStatus(RebaseStatus.REBASE_CONFLICTS, e.getMessage(), key);
		}
	}

	private void updateRebaseStatus(RebaseStatus status, String message, String key) {
		ProcessStatus projectRebaseStatus = new ProcessStatus();
		projectRebaseStatus.setStatus(status.getLabel());
		projectRebaseStatus.setMessage(message);
		this.rebaseStatusCache.put(key, projectRebaseStatus);
	}

	private AuthoringProject retrieveProject(String projectKey) throws BusinessServiceException {
		return projectServiceFactory.getInstanceByKey(projectKey).retrieveProject(projectKey, true);
	}

	private String parseKey(String projectKey, String taskKey) {
		return projectKey + "|" + taskKey;
	}

	@PreDestroy
	public void shutdown() {
		executorService.shutdown();
	}
}
