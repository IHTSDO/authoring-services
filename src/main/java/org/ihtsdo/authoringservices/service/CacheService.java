package org.ihtsdo.authoringservices.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CacheService {

    @Autowired
    private SnowstormClassificationClient classificationService;

    public void clearClassificationCache(String branchPath) {
        classificationService.evictClassificationCache(branchPath);
    }
}
