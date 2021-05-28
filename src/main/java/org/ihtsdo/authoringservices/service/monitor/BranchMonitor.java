package org.ihtsdo.authoringservices.service.monitor;

import org.ihtsdo.authoringservices.domain.EntityType;
import org.ihtsdo.authoringservices.domain.Notification;
import org.ihtsdo.authoringservices.service.BranchService;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Branch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class BranchMonitor extends Monitor {

	private final String projectId;
	private final String taskId;
	private final String branchPath;
	private final BranchService branchService;
	private final Logger logger = LoggerFactory.getLogger(getClass());
	private String branchState;
	private long branchHead = -1;

	public BranchMonitor(String projectId, String taskId, String branchPath, BranchService branchService) {
		this.projectId = projectId;
		this.taskId = taskId;
		this.branchPath = branchPath;
		this.branchService = branchService;
	}

	@Override
	public Notification runOnce() throws MonitorException {
		try {
			logger.info("Get branch state {}", branchPath);
			final Branch branch = branchService.getBranch(branchPath);
			String newBranchState = branch.getState();
			if (!newBranchState.equals(this.branchState)) {
				logger.info("Branch {} state {}, changed", branchPath, newBranchState);
				this.branchState = newBranchState;
				return new Notification(projectId, taskId, EntityType.BranchState, newBranchState);
			} else {
				logger.info("Branch {} state {}, no change", branchPath, newBranchState);
			}
			long newBranchHead = branch.getHeadTimestamp();
			if (newBranchHead != this.branchHead) {
				logger.info("Branch {} head {}, changed", branchPath, newBranchHead);
				this.branchHead = newBranchHead;
				return new Notification(projectId, taskId, EntityType.BranchHead, Long.toString(newBranchHead));
			} else {
				logger.info("Branch {} head {}, no change", taskId, newBranchHead);
			}
			return null;
		} catch (Exception e) {
			throw new MonitorException("Failed to get branch state", e);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		BranchMonitor that = (BranchMonitor) o;

		if (!Objects.equals(projectId, that.projectId)) return false;
		return Objects.equals(taskId, that.taskId);
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
