package org.ihtsdo.authoringservices.service;

import org.ihtsdo.authoringservices.domain.LineItem;
import org.ihtsdo.authoringservices.service.client.ReleaseNoteClient;
import org.ihtsdo.authoringservices.service.client.ReleaseNoteClientFactory;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReleaseNoteService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private ReleaseNoteClientFactory releaseNoteClientFactory;

    public void promoteTaskLineItems(String branchPath, String userDisplayName) {
        ReleaseNoteClient client = releaseNoteClientFactory.getClient();
        try {
            List<LineItem> lineItems = client.getLineItems(branchPath);
            for (LineItem lineItem : lineItems) {
                lineItem.setContent(branchPath + " - " + userDisplayName + "\n\n" + lineItem.getContent());
                client.updateLineItem(branchPath, lineItem);
                client.promoteLineItem(branchPath, lineItem.getId());
            }
        } catch (RestClientException e) {
            logger.error("Failed to promote task line items. Error: {}", e);
        }
    }

    public void promoteProjectLineItems(String branchPath) {
        ReleaseNoteClient client = releaseNoteClientFactory.getClient();
        try {
            client.promoteAllLineItems(branchPath);
        } catch (RestClientException e) {
            logger.error("Failed to promote line items. Error: {}", e);
        }
    }
}
