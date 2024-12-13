package org.ihtsdo.authoringservices.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.ihtsdo.authoringservices.domain.AuthoringProject;
import org.ihtsdo.authoringservices.domain.ProcessStatus;
import org.ihtsdo.authoringservices.service.factory.ProjectServiceFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.PathHelper;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ApiError;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Merge;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class RebaseService {

	private static final String REBASE_ERROR_STATUS = "Rebase Error";

	@Autowired
	private ProjectServiceFactory projectServiceFactory;

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

	public void doTaskRebase(String projectKey, String taskKey) {
		
		final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		ProcessStatus taskProcessStatus = new ProcessStatus();
		executorService.submit(() -> {
			SecurityContextHolder.getContext().setAuthentication(authentication);
			try {

				taskProcessStatus.setStatus("Rebasing");
				taskRebaseStatus.put(parseKey(projectKey, taskKey), taskProcessStatus);
				String taskBranchPath = branchService.getTaskBranchPathUsingCache(projectKey, taskKey);
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
					taskProcessStatus.setStatus(REBASE_ERROR_STATUS);
					taskProcessStatus.setMessage(message);
					taskRebaseStatus.put(parseKey(projectKey, taskKey), taskProcessStatus);
				}
			} catch (BusinessServiceException e) {
				e.printStackTrace();
				taskProcessStatus.setStatus(REBASE_ERROR_STATUS);
				taskProcessStatus.setMessage(e.getMessage());
				taskRebaseStatus.put(parseKey(projectKey, taskKey), taskProcessStatus);
			}
		});
	}
	
	public void doProjectRebase(String projectKey) throws BusinessServiceException {
		AuthoringProject project = retrieveProject(projectKey);
		if (Boolean.TRUE.equals(project.isProjectRebaseDisabled())|| Boolean.TRUE.equals(project.isProjectLocked())) {
			throw new BusinessServiceException("Project rebase is disabled");
		}

		final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		ProcessStatus projectProcessStatus = new ProcessStatus();
		executorService.submit(() -> {
			SecurityContextHolder.getContext().setAuthentication(authentication);
			try {

				projectProcessStatus.setStatus("Rebasing");
				projectRebaseStatus.put(projectKey, projectProcessStatus);
				String projectBranchPath = branchService.getProjectBranchPathUsingCache(projectKey);
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
					projectProcessStatus.setStatus(REBASE_ERROR_STATUS);
					projectProcessStatus.setMessage(message);
					projectRebaseStatus.put(projectKey, projectProcessStatus);
				}
			} catch (BusinessServiceException e) {
				e.printStackTrace();
				projectProcessStatus.setStatus(REBASE_ERROR_STATUS);
				projectProcessStatus.setMessage(e.getMessage());
				projectRebaseStatus.put(projectKey, projectProcessStatus);
			}
		});
		
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

	public ProcessStatus getTaskRebaseStatus(String projectKey, String taskKey) {
		return taskRebaseStatus.get(parseKey(projectKey, taskKey));
	}

	public ProcessStatus getProjectRebaseStatus(String projectKey) {
		return projectRebaseStatus.get(projectKey);
	}

	
}
