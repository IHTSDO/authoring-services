package org.ihtsdo.authoringservices.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

@Service
public class CacheService {

    @Autowired
    private SnowstormClassificationClient classificationService;

    @Autowired
    private BranchServiceCache branchServiceCache;

    @Autowired
    private CacheManager cacheManager;

    public void clearClassificationCache(String branchPath) {
        classificationService.evictClassificationCache(branchPath);
    }

    public void clearBranchCache(String branchPath) {
        branchServiceCache.evictBranchCache(branchPath);
    }

    public void clearBranchCacheStartWith(String branchStartWith) {
        Set<Object> keys = getBranchCaches();
        if (keys != null) {
            keys.stream().filter(key -> key.toString().startsWith(branchStartWith)).forEach(item -> branchServiceCache.evictBranchCache(item.toString()));
        }
    }

    @Nullable
    public Set<Object> getBranchCaches() {
        Cache cache = cacheManager.getCache("branchCache");
        Set<Object> keys = null;
        if (cache instanceof org.springframework.cache.caffeine.CaffeineCache caffeinecache) {
            com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = caffeinecache.getNativeCache();
            keys = nativeCache.asMap().keySet();
        } else if (cache instanceof org.springframework.cache.concurrent.ConcurrentMapCache concurrentmapcache) {
            ConcurrentMap<Object, Object> nativeCache = concurrentmapcache.getNativeCache();
            keys = nativeCache.keySet();
        }
        return keys;
    }
}
