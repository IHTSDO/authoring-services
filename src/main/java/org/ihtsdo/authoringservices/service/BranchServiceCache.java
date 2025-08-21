package org.ihtsdo.authoringservices.service;

import org.ihtsdo.authoringservices.service.exceptions.ServiceException;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Branch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

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
            return toNewBranch(snowstormRestClientFactory.getClient().getBranch(branchPath));
        } catch (RestClientException e) {
            throw new ServiceException("Failed to fetch branch " + branchPath, e);
        }
    }

    @CacheEvict(value = "branchCache", key = "#branchPath")
    public void evictBranchCache(String branchPath) {
        logger.debug("Cleared Branch cache for branch {}.", branchPath);
    }

    private Branch toNewBranch(Branch snowstormBranch) {
        if (snowstormBranch == null) return null;
        Branch branch = new Branch();
        branch.setName(snowstormBranch.getName());
        branch.setPath(snowstormBranch.getPath());
        branch.setState(snowstormBranch.getState());
        branch.setDeleted(snowstormBranch.isDeleted());
        branch.setBaseTimestamp(snowstormBranch.getBaseTimestamp());
        branch.setHeadTimestamp(snowstormBranch.getHeadTimestamp());
        branch.setUserRoles(snowstormBranch.getUserRoles());
        branch.setGlobalUserRoles(snowstormBranch.getGlobalUserRoles());
        if (snowstormBranch.getMetadata() != null) {
            Map<String, Object> metadata = new HashMap<>(snowstormBranch.getMetadata());
            branch.setMetadata(metadata);
        }

        return branch;
    }
}
