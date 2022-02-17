package org.ihtsdo.authoringservices.service;

import org.ihtsdo.authoringservices.domain.LineItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class ReleaseNoteService {

    @Value("${release-notes.url}")
    private String releaseNotesUrl;

    private final RestTemplate releaseNotesRestTemplate = new RestTemplate();

    private static final ParameterizedTypeReference<List <LineItem>> LINE_ITEM_LIST_TYPE_REF = new ParameterizedTypeReference<>(){};

    public void promoteBranchLineItems(String taskBranchPathUsingCache) {
        ResponseEntity <List <LineItem>> response = releaseNotesRestTemplate.exchange(
                releaseNotesUrl + taskBranchPathUsingCache + "/lineitems",
                HttpMethod.GET,
                null,
                LINE_ITEM_LIST_TYPE_REF);
        if (response != null && response.hasBody()) {
            List <LineItem> lineItems = response.getBody();
            for (LineItem lineItem : lineItems) {
                releaseNotesRestTemplate.postForObject(releaseNotesUrl + taskBranchPathUsingCache + "/lineitems/" + lineItem.getId() + "/promote", null, Void.class);
            }
        }
    }
}
