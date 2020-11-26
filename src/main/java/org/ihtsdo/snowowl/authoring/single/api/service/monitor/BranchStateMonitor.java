package org.ihtsdo.snowowl.authoring.single.api.service.monitor;

import org.ihtsdo.snowowl.authoring.single.api.pojo.EntityType;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Notification;
import org.ihtsdo.snowowl.authoring.single.api.service.BranchService;
import org.ihtsdo.snowowl.authoring.single.api.service.exceptions.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BranchStateMonitor extends Monitor {

	private final String projectId;
	private final String taskId;
	private final String branchPath;
	private BranchService branchService;
	private String branchState;
	private Logger logger = LoggerFactory.getLogger(getClass());

	public BranchStateMonitor(String projectId, String taskId, String branchPath, BranchService branchService) {
		this.projectId = projectId;
		this.taskId = taskId;
		this.branchPath = branchPath;
		this.branchService = branchService;
	}

	@Override
	public Notification runOnce() throws MonitorException {
		try {
			logger.debug("Get branch state");
			final String branchState = branchService.getBranchState(branchPath);
			if (branchState != this.branchState) {
				logger.debug("Branch {} state {}, changed", taskId, branchState);
				this.branchState = branchState;
				return new Notification(projectId, taskId, EntityType.BranchState, branchState);
			} else {
				logger.debug("Branch {} state {}, no change", taskId, branchState);
			}
			return null;
		} catch (ServiceException e) {
			throw new MonitorException("Failed to get branch state", e);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		BranchStateMonitor that = (BranchStateMonitor) o;

		if (projectId != null ? !projectId.equals(that.projectId) : that.projectId != null) return false;
		return !(taskId != null ? !taskId.equals(that.taskId) : that.taskId != null);
	}

	@Override
	public int hashCode() {
		int result = projectId != null ? projectId.hashCode() : 0;
		result = 31 * result + (taskId != null ? taskId.hashCode() : 0);
		return result;
	}

	@Override
	public String toString() {
		return "BranchStateMonitor{" +
				"projectId='" + projectId + '\'' +
				", taskId='" + taskId + '\'' +
				'}';
	}
}
