package org.ihtsdo.authoringservices.service.client;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Service
public class TraceabilityClientFactory {

	@Value("${traceability-service.url:}")
	private String traceabilityUrl;

	private final Cache<String, TraceabilityClient> clientCache;

	public TraceabilityClientFactory() {
		this.clientCache = CacheBuilder.newBuilder().expireAfterAccess(5L, TimeUnit.MINUTES).build();
	}

	public TraceabilityClient getClient() {
		if (!StringUtils.hasLength(traceabilityUrl)) {
			return null;
		}
		TraceabilityClient client = null;
		String authenticationToken = SecurityUtil.getAuthenticationToken();
		if (StringUtils.hasLength(authenticationToken)) {
			client = this.clientCache.getIfPresent(authenticationToken);
		}
		if (client == null) {
			synchronized (this.clientCache) {
				authenticationToken = SecurityUtil.getAuthenticationToken();
				if (StringUtils.hasLength(authenticationToken)) {
					client = this.clientCache.getIfPresent(authenticationToken);
				}
				if (client == null) {
					client = new TraceabilityClient(traceabilityUrl, authenticationToken);
					if (StringUtils.hasLength(authenticationToken)) {
						this.clientCache.put(authenticationToken, client);
					}
				}
			}
		}
		return client;
	}
}
