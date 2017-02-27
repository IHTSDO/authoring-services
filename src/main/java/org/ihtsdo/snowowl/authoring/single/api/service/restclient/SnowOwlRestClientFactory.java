package org.ihtsdo.snowowl.authoring.single.api.service.restclient;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import org.ihtsdo.otf.rest.client.snowowl.SnowOwlRestClient;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SnowOwlRestClientFactory {

	private String snowOwlUrl;
	private final Cache<String, SnowOwlRestClient> clientCache;

	public SnowOwlRestClientFactory(String snowOwlUrl) {
		this.snowOwlUrl = snowOwlUrl;
		clientCache = CacheBuilder.newBuilder()
				.expireAfterAccess(5, TimeUnit.MINUTES)
				.build();
	}

	/**
	 * Creates a Snow Owl client using the authentication context of the current thread.
	 * @return
	 */
	public SnowOwlRestClient getClient() {
		String authenticationToken = SecurityUtil.getAuthenticationToken();
		SnowOwlRestClient client = clientCache.getIfPresent(authenticationToken);
		if (client == null) {
			synchronized (clientCache) {
				client = clientCache.getIfPresent(authenticationToken);
				if (client == null) {
					client = new SnowOwlRestClient(snowOwlUrl, authenticationToken);
					clientCache.put(authenticationToken, client);
				}
			}
		}
		return client;
	}

}
