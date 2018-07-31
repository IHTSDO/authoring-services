package org.ihtsdo.snowowl.authoring.single.api.service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PreDestroy;

import org.ihtsdo.otf.rest.client.snowowl.PathHelper;
import org.ihtsdo.otf.rest.client.snowowl.pojo.ApiError;
import org.ihtsdo.otf.rest.client.snowowl.pojo.Merge;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.ProcessStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RebaseService {

	@Autowired
	private TaskService taskService;

	@Autowired
	private BranchService branchService;

	private final Map<String, ProcessStatus> taskRebaseStatus;
	
	private final Map<String, ProcessStatus> projectRebaseStatus;

	private final ExecutorService executorService;

	public RebaseService() {
		taskRebaseStatus = new HashMap<>();
		projectRebaseStatus = new HashMap<>(); 
		executorService = Executors.newCachedThreadPool();
	}

	public void doTaskRebase(String projectKey, String taskKey) throws BusinessServiceException {
		
		final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		ProcessStatus taskProcessStatus = new ProcessStatus();
		executorService.submit(() -> {
			SecurityContextHolder.getContext().setAuthentication(authentication);
			try {

				taskProcessStatus.setStatus("Rebasing");
				taskRebaseStatus.put(parseKey(projectKey, taskKey), taskProcessStatus);
				String taskBranchPath = taskService.getTaskBranchPathUsingCache(projectKey, taskKey);
				Merge merge = branchService.mergeBranchSync(PathHelper.getParentPath(taskBranchPath), taskBranchPath,
						null);
				if (merge.getStatus() == Merge.Status.COMPLETED) {
					taskProcessStatus.setStatus("Rebase Complete");
					taskRebaseStatus.put(parseKey(projectKey, taskKey), taskProcessStatus);
				} else if (merge.getStatus().equals(Merge.Status.CONFLICTS)) {
					try {
						ObjectMapper mapper = new ObjectMapper();
						String jsonInString = mapper.writeValueAsString(merge);
						taskProcessStatus.setStatus(Merge.Status.CONFLICTS.name());
						taskProcessStatus.setMessage(jsonInString);
						taskRebaseStatus.put(parseKey(projectKey, taskKey), taskProcessStatus);
					} catch (JsonProcessingException e) {
						e.printStackTrace();
					}
				} else {
					ApiError apiError = merge.getApiError();
					String message = apiError != null ? apiError.getMessage() : null;
					taskProcessStatus.setStatus("Rebase Error");
					taskProcessStatus.setMessage(message);
					taskRebaseStatus.put(parseKey(projectKey, taskKey), taskProcessStatus);
				}
			} catch (BusinessServiceException e) {
				e.printStackTrace();
				taskProcessStatus.setStatus("Rebase Error");
				taskProcessStatus.setMessage(e.getMessage());
				taskRebaseStatus.put(parseKey(projectKey, taskKey), taskProcessStatus);
			}
		});
	}
	
	public void doProjectRebase(String projectKey) {
		final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		ProcessStatus projectProcessStatus = new ProcessStatus();
		executorService.submit(() -> {
			SecurityContextHolder.getContext().setAuthentication(authentication);
			try {

				projectProcessStatus.setStatus("Rebasing");
				projectRebaseStatus.put(projectKey, projectProcessStatus);
				String projectBranchPath = taskService.getProjectBranchPathUsingCache(projectKey);
				Merge merge = branchService.mergeBranchSync(PathHelper.getParentPath(projectBranchPath), projectBranchPath, null);
				if (merge.getStatus() == Merge.Status.COMPLETED) {
					projectProcessStatus.setStatus("Rebase Complete");
					projectRebaseStatus.put(projectKey, projectProcessStatus);
				} else if (merge.getStatus().equals(Merge.Status.CONFLICTS)) {
					try {
						ObjectMapper mapper = new ObjectMapper();
						String jsonInString = mapper.writeValueAsString(merge);
						projectProcessStatus.setStatus(Merge.Status.CONFLICTS.name());
						projectProcessStatus.setMessage(jsonInString);
						projectRebaseStatus.put(projectKey, projectProcessStatus);
					} catch (JsonProcessingException e) {
						e.printStackTrace();
					}
				} else {
					ApiError apiError = merge.getApiError();
					String message = apiError != null ? apiError.getMessage() : null;
					projectProcessStatus.setStatus("Rebase Error");
					projectProcessStatus.setMessage(message);
					projectRebaseStatus.put(projectKey, projectProcessStatus);
				}
			} catch (BusinessServiceException e) {
				e.printStackTrace();
				projectProcessStatus.setStatus("Rebase Error");
				projectProcessStatus.setMessage(e.getMessage());
				projectRebaseStatus.put(projectKey, projectProcessStatus);
			}
		});
		
	}

	private String parseKey(String projectKey, String taskKey) {
		return projectKey + "|" + taskKey;
	}

	@PreDestroy
	public void shutdown() {
		executorService.shutdown();
	}

	public ProcessStatus getTaskRebaseStatus(String projectKey, String taskKey) {
		return taskRebaseStatus.get(parseKey(projectKey, taskKey));
	}

	public ProcessStatus getProjectRebaseStatus(String projectKey) {
		return projectRebaseStatus.get(projectKey);
	}

	
}
