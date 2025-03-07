package org.ihtsdo.authoringservices.service.client;

import org.ihtsdo.sso.integration.SecurityUtil;
import org.springframework.stereotype.Service;

@Service
public class ContentRequestServiceClientFactory {
    public ContentRequestServiceClient getClient(String url) {
        String authenticationToken = SecurityUtil.getAuthenticationToken();
        return new ContentRequestServiceClient(url, authenticationToken);
    }
}

