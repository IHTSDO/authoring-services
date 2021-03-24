package org.ihtsdo.authoringservices.service.monitor;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.authoringservices.service.BranchService;
import org.ihtsdo.authoringservices.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MonitorFactory {

	@Autowired
	private BranchService branchService;

	@Autowired
	private TaskService taskService;

	public Monitor createMonitor(String focusProjectId, String focusTaskId) throws BusinessServiceException {
		final String branchPath = taskService.getTaskBranchPathUsingCache(focusProjectId, focusTaskId);
		return new BranchStateMonitor(focusProjectId, focusTaskId, branchPath, branchService);
	}

}
