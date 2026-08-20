package org.ihtsdo.authoringservices.service;

import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ConceptMiniPojo;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class LanguageRefsetService {

	static final String LANGUAGE_REFSET_TYPE_ID = "900000000000506000";

	private final SnowstormRestClientFactory snowstormRestClientFactory;

	public LanguageRefsetService(SnowstormRestClientFactory snowstormRestClientFactory) {
		this.snowstormRestClientFactory = snowstormRestClientFactory;
	}

	public List<ConceptMiniPojo> getLanguageRefsets(String branchPath, String acceptLanguage) {
		SnowstormRestClient client = snowstormRestClientFactory.getClient();
		Map<String, ConceptMiniPojo> refsets = client.getRefsetsWithTypeInformation(branchPath, true, null,
				LANGUAGE_REFSET_TYPE_ID, acceptLanguage);
		if (CollectionUtils.isEmpty(refsets)) {
			return List.of();
		}
		return new ArrayList<>(refsets.values());
	}
}
