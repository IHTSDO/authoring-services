package org.ihtsdo.authoringservices.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.ihtsdo.authoringservices.domain.User;
import org.ihtsdo.authoringservices.service.client.IMSClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Service for caching user details to reduce IMS calls.
 * Provides both individual user lookups and batch user loading capabilities.
 */
@Service
public class UserCacheService {

    private static final Logger logger = LoggerFactory.getLogger(UserCacheService.class);

    @Autowired
    private IMSClientFactory imsClientFactory;

    @Value("${user.cache.expiry.minutes:30}")
    private int cacheExpiryMinutes;

    @Value("${user.cache.maximum.size:1000}")
    private int cacheMaximumSize;

    @Value("${user.cache.batch.enabled:true}")
    private boolean batchLoadingEnabled;

    private Cache<String, User> userCache;

    @PostConstruct
    public void init() {
        this.userCache = CacheBuilder.newBuilder()
                .expireAfterWrite(cacheExpiryMinutes, TimeUnit.MINUTES)
                .maximumSize(cacheMaximumSize)
                .recordStats()
                .build();
        
        logger.info("UserCacheService initialized with {} minute expiry, max size {}, batch loading {}", 
                cacheExpiryMinutes, cacheMaximumSize, batchLoadingEnabled);
    }

    /**
     * Get a single user by username. Uses cache first, then IMS if not found.
     * 
     * @param username The username to look up
     * @return User object, or a minimal User with just username if not found in IMS
     */
    public User getUser(String username) {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }

        String normalizedUsername = username.trim();
        User cachedUser = userCache.getIfPresent(normalizedUsername);
        
        if (cachedUser != null) {
            logger.debug("User '{}' found in cache", normalizedUsername);
            return cachedUser;
        }

        logger.debug("User '{}' not in cache, fetching from IMS", normalizedUsername);
        User user = fetchUserFromIMS(normalizedUsername);
        userCache.put(normalizedUsername, user);
        
        return user;
    }

    /**
     * Get multiple users efficiently. Uses cache first, then batch loads missing users from IMS.
     * 
     * @param usernames Collection of usernames to look up
     * @return Map of username to User object
     */
    public Map<String, User> getUsers(Collection<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return Collections.emptyMap();
        }

        // Normalize and deduplicate usernames
        Set<String> normalizedUsernames = usernames.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        if (normalizedUsernames.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, User> result = new HashMap<>();
        Set<String> uncachedUsernames = new HashSet<>();

        // Check cache first
        for (String username : normalizedUsernames) {
            User cachedUser = userCache.getIfPresent(username);
            if (cachedUser != null) {
                result.put(username, cachedUser);
                logger.debug("User '{}' found in cache", username);
            } else {
                uncachedUsernames.add(username);
            }
        }

        // Fetch uncached users
        if (!uncachedUsernames.isEmpty()) {
            logger.debug("Fetching {} uncached users from IMS: {}", uncachedUsernames.size(), uncachedUsernames);
            
            if (batchLoadingEnabled && uncachedUsernames.size() > 1) {
                // Batch load uncached users
                Map<String, User> fetchedUsers = batchFetchUsersFromIMS(uncachedUsernames);
                result.putAll(fetchedUsers);
                
                // Cache the fetched users
                fetchedUsers.forEach((username, user) -> userCache.put(username, user));
            } else {
                // Individual fetch for each uncached user
                for (String username : uncachedUsernames) {
                    User user = fetchUserFromIMS(username);
                    result.put(username, user);
                    userCache.put(username, user);
                }
            }
        }

        logger.debug("Returning {} users, {} from cache, {} fetched from IMS", 
                result.size(), normalizedUsernames.size() - uncachedUsernames.size(), uncachedUsernames.size());

        return result;
    }

    /**
     * Preload users into cache. Useful for warming cache before bulk operations.
     * 
     * @param usernames Collection of usernames to preload
     */
    public void preloadUsers(Collection<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return;
        }

        logger.debug("Preloading {} users into cache", usernames.size());
        getUsers(usernames); // This will populate the cache
    }

    /**
     * Clear all cached users
     */
    public void clearCache() {
        userCache.invalidateAll();
        logger.info("User cache cleared");
    }

    /**
     * Get cache statistics
     * 
     * @return Map with cache statistics
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("size", userCache.size());
        stats.put("hitRate", userCache.stats().hitRate());
        stats.put("missRate", userCache.stats().missRate());
        stats.put("hitCount", userCache.stats().hitCount());
        stats.put("missCount", userCache.stats().missCount());
        stats.put("loadCount", userCache.stats().loadCount());
        stats.put("averageLoadTimeNanos", userCache.stats().averageLoadPenalty());
        return stats;
    }

    /**
     * Fetch a single user from IMS
     */
    private User fetchUserFromIMS(String username) {
        try {
            return imsClientFactory.getClient().getUserDetails(username);
        } catch (RestClientResponseException e) {
            if (HttpStatusCode.valueOf(404).equals(e.getStatusCode())) {
                logger.warn("User '{}' not found in IMS, returning minimal user object", username);
                User user = new User();
                user.setUsername(username);
                return user;
            }
            logger.error("Error fetching user '{}' from IMS: {}", username, e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error fetching user '{}' from IMS: {}", username, e.getMessage(), e);
            // Return minimal user object as fallback
            User user = new User();
            user.setUsername(username);
            return user;
        }
    }

    /**
     * Batch fetch multiple users from IMS.
     * 
     * Current implementation uses individual IMS calls since the IMS API does not provide
     * a batch user lookup endpoint. This method optimizes error handling by ensuring
     * failed lookups don't break the entire batch operation - partial failures return
     * minimal user objects with just the username populated.
     * 
     * If IMS adds batch user lookup support in the future, this method can be updated
     * to use a single API call instead of individual requests.
     */
    private Map<String, User> batchFetchUsersFromIMS(Set<String> usernames) {
        Map<String, User> result = new HashMap<>();
        
        logger.debug("Fetching {} users individually (IMS has no batch endpoint)", usernames.size());
        
        for (String username : usernames) {
            try {
                User user = fetchUserFromIMS(username);
                result.put(username, user);
            } catch (Exception e) {
                logger.warn("Failed to fetch user '{}' during batch operation: {}", username, e.getMessage());
                // Still add a minimal user object so callers don't get null
                User user = new User();
                user.setUsername(username);
                result.put(username, user);
            }
        }
        
        return result;
    }
}
