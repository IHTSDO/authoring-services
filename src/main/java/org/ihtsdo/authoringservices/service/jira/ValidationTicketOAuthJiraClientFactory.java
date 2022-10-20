package org.ihtsdo.authoringservices.service.jira;

import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.authoringservices.service.exceptions.UnauthorizedServiceException;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;

@Service
@Qualifier("validationTicketOAuthJiraClient")
public class ValidationTicketOAuthJiraClientFactory implements ImpersonatingJiraClientFactory {

	private final String jiraUrl;
	private final String consumerKey;
	private final PrivateKey privateKey;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public ValidationTicketOAuthJiraClientFactory(
	        @Value("${rvf.jira.url}") String jiraUrl,
            @Value("${rvf.jira.consumerKey}") String consumerKey,
            @Value("${rvf.jira.privateKeyName}") String privateKeyPath) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {

		this.jiraUrl = jiraUrl;
		this.consumerKey = consumerKey;
		if (StringUtils.hasLength(privateKeyPath)) {
			this.privateKey = OAuthCredentials.getPrivateKey(privateKeyPath);
		} else {
			this.privateKey = null;
		}
	}

	/**
	 * Get an instance of JiraClient that will make signed OAuth requests as the specified username.
	 */
	public JiraClient getImpersonatingInstance(String username) {
		if (username == null) {
			logger.error("Jira client requested but with NULL username! In Spring Security session Authentication Token is '{}', Username is '{}'",
					SecurityUtil.getAuthenticationToken(), SecurityUtil.getUsername());
			throw new UnauthorizedServiceException("Denied Jira access.");
		}

		return doGetJiraClient(username);
	}

	@Override
	public JiraClient getAdminInstance() {
		return null;
	}

	private JiraClient doGetJiraClient(String username) {
		try {
			return new JiraClient(jiraUrl, new OAuthCredentials(username, consumerKey, privateKey));
		} catch (JiraException e) {
			throw new RuntimeException("Failed to create JiraClient.", e);
		}
	}

}
