package org.ihtsdo.authoringservices.service;

import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.CodeSystemUpgradeJob;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import static org.ihtsdo.otf.rest.client.terminologyserver.pojo.CodeSystemUpgradeJob.UpgradeStatus.COMPLETED;
import static org.ihtsdo.otf.rest.client.terminologyserver.pojo.CodeSystemUpgradeJob.UpgradeStatus.FAILED;

@Service
public class CodeSystemUpgradeService {
	@Autowired
	private SnowstormRestClientFactory snowstormRestClientFactory;

	public String upgrade(String shortName, Integer newDependantVersion) throws BusinessServiceException {
		String location = snowstormRestClientFactory.getClient().upgradeCodeSystem(shortName, newDependantVersion, false);
		return location.substring(location.lastIndexOf("/") + 1);
	}

	public CodeSystemUpgradeJob getUpgradeJob(String jobId) throws RestClientException {
		return snowstormRestClientFactory.getClient().getCodeSystemUpgradeJob(jobId);
	}

	@Async
	public void waitForCodeSystemUpgradeToComplete(String jobId, SecurityContext securityContext) throws BusinessServiceException {
		SecurityContextHolder.setContext(securityContext);
		SnowstormRestClient client = snowstormRestClientFactory.getClient();
		CodeSystemUpgradeJob codeSystemUpgradeJob;
		int sleepSeconds = 4;
		int totalWait = 0;
		int maxTotalWait = 60 * 60;
		try {
			do {
				Thread.sleep(1000 * sleepSeconds);
				totalWait += sleepSeconds;
				codeSystemUpgradeJob = client.getCodeSystemUpgradeJob(jobId);
				if (sleepSeconds < 10) {
					sleepSeconds+=2;
				}
			} while (totalWait < maxTotalWait && (COMPLETED.equals(codeSystemUpgradeJob.getStatus()) || FAILED.equals(codeSystemUpgradeJob.getStatus())));

			if (codeSystemUpgradeJob != null && COMPLETED.equals(codeSystemUpgradeJob.getStatus())) {

			}
		} catch (InterruptedException | RestClientException e) {
			throw new BusinessServiceException("Failed to fetch code system upgrade status.", e);
		}
	}
}
