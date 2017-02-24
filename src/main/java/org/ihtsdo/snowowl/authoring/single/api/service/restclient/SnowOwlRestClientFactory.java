package org.ihtsdo.snowowl.authoring.single.api.service.restclient;

import org.ihtsdo.otf.rest.client.snowowl.SnowOwlRestClient;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.springframework.beans.factory.annotation.Value;

public class SnowOwlRestClientFactory {

	private String snowOwlUrl;

	public SnowOwlRestClientFactory(String snowOwlUrl) {
		this.snowOwlUrl = snowOwlUrl;
	}

	/**
	 * Creates a Snow Owl client using the authentication context of the current thread.
	 * @return
	 */
	public SnowOwlRestClient getClient() {
		return new SnowOwlRestClient(snowOwlUrl, SecurityUtil.getAuthenticationToken());
	}

}
