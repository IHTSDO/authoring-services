package org.ihtsdo.authoringservices.service.monitor;

import org.ihtsdo.authoringservices.service.BranchService;
import org.ihtsdo.authoringservices.service.CacheService;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MonitorFactory {

	@Autowired
	private BranchService branchService;

	@Autowired
	private CacheService cacheService;

	public Monitor createMonitor(String focusProjectId, String focusTaskId) throws BusinessServiceException {
		final String branchPath = branchService.getProjectOrTaskBranchPathUsingCache(focusProjectId, focusTaskId);
		return new BranchMonitor(focusProjectId, focusTaskId, branchPath, branchService, cacheService);
	}

}
