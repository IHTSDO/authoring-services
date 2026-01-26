package org.ihtsdo.authoringservices.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.PostConstruct;
import org.ihtsdo.authoringservices.domain.User;
import org.ihtsdo.authoringservices.service.client.IMSClientFactory;
import org.ihtsdo.otf.rest.client.ims.IMSRestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Service for caching user details to reduce IMS calls.
 * Provides both individual user lookups and batch user loading capabilities.
 */
@Service
public class UserCacheService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final IMSClientFactory imsClientFactory;

    @Value("${jira.groupName}")
    private String defaultGroupName;

    @Value("${auto.rebase.username}")
    private String imsUsername;

    @Value("${auto.rebase.password}")
    private String imsPassword;

    @Value("${ims.url}")
    private String imsUrl;

    @Value("${user.cache.expiry.minutes}")
    private int cacheExpiryMinutes;

    @Value("${user.cache.maximum.size:1000}")
    private int cacheMaximumSize;

    private Cache<String, User> userCache;

    private Cache<String, List<User>> userGroupCache;

    @PostConstruct
    public void init() {
        this.userCache = CacheBuilder.newBuilder()
                .expireAfterWrite(cacheExpiryMinutes, TimeUnit.MINUTES)
                .maximumSize(cacheMaximumSize)
                .recordStats()
                .build();

        this.userGroupCache = CacheBuilder.newBuilder()
                .expireAfterWrite(cacheExpiryMinutes, TimeUnit.MINUTES)
                .maximumSize(cacheMaximumSize)
                .recordStats()
                .build();
        
        logger.info("UserCacheService initialized with {} minute expiry, max size {}",
                cacheExpiryMinutes, cacheMaximumSize);
    }

    public UserCacheService(IMSClientFactory imsClientFactory) {
        this.imsClientFactory = imsClientFactory;
    }

    @Scheduled(initialDelay = 1, fixedRateString = "${user.cache.expiry.minutes}", timeUnit = TimeUnit.MINUTES)
    public void preloadUsersForDefaultGroup() throws URISyntaxException, IOException {
        if (imsUsername == null || imsUsername.isEmpty()) {
			logger.warn("Username is null or empty (configuration: auto.rebase.username), unable to preload users");
			return;
        }
		loginToIMSAndSetSecurityContext();
        Set<String> keys = new HashSet<>(userGroupCache.asMap().keySet());
        keys.add(defaultGroupName);
        for (String key : keys) {
            userGroupCache.put(key, doGetUsersForGroup(key));
        }
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
     * Load multiple users efficiently. Uses cache first, then loads missing users from IMS.
     *
     * @param usernames Collection of usernames to look up
     */
    public void loadUsers(Collection<String> usernames) {
        // Normalize and deduplicate usernames
        Set<String> normalizedUsernames = usernames.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        if (normalizedUsernames.isEmpty()) {
            return;
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
            // Individual fetch for each uncached user
            for (String username : uncachedUsernames) {
                User user = fetchUserFromIMS(username);
                result.put(username, user);
                userCache.put(username, user);
            }
        }

        logger.debug("Returning {} users, {} from cache, {} fetched from IMS", 
                result.size(), normalizedUsernames.size() - uncachedUsernames.size(), uncachedUsernames.size());

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
        loadUsers(usernames); // This will populate the cache
    }

    /**
     * Clear all cached users
     */
    public void clearCache() {
        userCache.invalidateAll();
        userGroupCache.invalidateAll();
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

    public List<User> getAllUsersForGroup(String groupName) {
        String groupNameToSearch = (StringUtils.hasLength(groupName) ? groupName : defaultGroupName).trim();
        List<User> allUsers = userGroupCache.getIfPresent(groupNameToSearch.trim());
        if (allUsers == null) {
            allUsers = doGetUsersForGroup(groupNameToSearch);
            userGroupCache.put(groupNameToSearch, allUsers);
        }

        return allUsers;
    }

    private List<User> doGetUsersForGroup(String groupName) {
        List<User> users = imsClientFactory.getClient().searchUserByGroupname(groupName, 0, -1);
        return users.stream().filter(User::isActive).toList();
    }

    /**
     * Fetch a single user from IMS
     */
    private User fetchUserFromIMS(String username) {
        try {
            return imsClientFactory.getClient().getUserDetails(username);
        } catch (Exception e) {
            logger.error("Unexpected error fetching user '{}' from IMS: {}", username, e.getMessage(), e);
            // Return minimal user object as fallback
            User user = new User();
            user.setUsername(username);
            return user;
        }
    }

    private void loginToIMSAndSetSecurityContext() throws URISyntaxException, IOException {
        IMSRestClient imsClient = new IMSRestClient(imsUrl);
        String token = imsClient.loginForceNewSession(imsUsername, imsPassword);
        PreAuthenticatedAuthenticationToken decoratedAuthentication = new PreAuthenticatedAuthenticationToken(imsUsername, token);
        SecurityContextHolder.getContext().setAuthentication(decoratedAuthentication);
    }
}
