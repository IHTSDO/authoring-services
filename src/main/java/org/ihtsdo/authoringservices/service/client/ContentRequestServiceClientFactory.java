package org.ihtsdo.authoringservices.service.client;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Service
public class ContentRequestServiceClientFactory {

    @Value("${crs.url}")
    private String contentRequestServiceUrl;

    private final Cache <String, ContentRequestServiceClient> clientCache;

    public ContentRequestServiceClientFactory() {
       this.clientCache = CacheBuilder.newBuilder().expireAfterAccess(5L, TimeUnit.MINUTES).build();
    }

    public ContentRequestServiceClient getClient(){
        ContentRequestServiceClient client = null;
        String authenticationToken = SecurityUtil.getAuthenticationToken();
        if (StringUtils.hasLength(authenticationToken)) {
            client = this.clientCache.getIfPresent(authenticationToken);
        }
        if (client == null) {
            synchronized(this.clientCache) {
                authenticationToken = SecurityUtil.getAuthenticationToken();
                if (StringUtils.hasLength(authenticationToken)) {
                    client = this.clientCache.getIfPresent(authenticationToken);
                }
                if (client == null) {
                    client = new ContentRequestServiceClient(contentRequestServiceUrl, authenticationToken);
                    authenticationToken = SecurityUtil.getAuthenticationToken();
                    this.clientCache.put(authenticationToken, client);
                }
            }
        }

        return client;
    }
}

