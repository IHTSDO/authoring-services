package org.ihtsdo.authoringservices.service;

import org.ihtsdo.authoringservices.domain.Branch;
import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class BranchServiceCache {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final SnowstormRestClientFactory snowstormRestClientFactory;

    public BranchServiceCache(@Autowired SnowstormRestClientFactory snowstormRestClientFactory) {
        this.snowstormRestClientFactory = snowstormRestClientFactory;
    }

    @Cacheable(value = "branchCache", key = "#branchPath", sync = true)
    public Branch getBranchOrNull(String branchPath) throws ServiceException {
        try {
            return toBranch(snowstormRestClientFactory.getClient().getBranch(branchPath));
        } catch (RestClientException e) {
            throw new ServiceException("Failed to fetch branch " + branchPath, e);
        }
    }

    @CacheEvict(value = "branchCache", key = "#branchPath")
    public void evictBranchCache(String branchPath) {
        logger.debug("Cleared Branch cache for branch {}.", branchPath);
    }

    private Branch toBranch(org.ihtsdo.otf.rest.client.terminologyserver.pojo.Branch snowstormBranch) {
        if (snowstormBranch == null) return null;
        Branch branch = new Branch();
        branch.setName(snowstormBranch.getName());
        branch.setPath(snowstormBranch.getPath());
        branch.setState(snowstormBranch.getState());
        branch.setDeleted(snowstormBranch.isDeleted());
        branch.setBaseTimestamp(snowstormBranch.getBaseTimestamp());
        branch.setHeadTimestamp(snowstormBranch.getHeadTimestamp());
        branch.setMetadata(snowstormBranch.getMetadata());
        branch.setUserRoles(snowstormBranch.getUserRoles());
        branch.setGlobalUserRoles(snowstormBranch.getGlobalUserRoles());

        return branch;
    }
}
