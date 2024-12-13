package org.ihtsdo.authoringservices.service.client;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Service
public class IMSClientFactory {

    @Value("${ims.url}")
    private String imsUrl;

    private final Cache<String, IMSClient> clientCache;

    public IMSClientFactory() {
        this.clientCache = CacheBuilder.newBuilder().expireAfterAccess(5L, TimeUnit.MINUTES).build();
    }

    public IMSClient getClient() {
        IMSClient client = null;
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
                    client = new IMSClient(imsUrl, authenticationToken);
                    authenticationToken = SecurityUtil.getAuthenticationToken();
                    this.clientCache.put(authenticationToken, client);
                }
            }
        }

        return client;
    }
}

