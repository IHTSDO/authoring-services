package org.ihtsdo.authoringservices.service.client;

import org.ihtsdo.authoringservices.domain.LineItem;
import org.ihtsdo.otf.rest.client.ExpressiveErrorHandler;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.ims.IMSRestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

public class ReleaseNoteClient {
    private static final Logger logger = LoggerFactory.getLogger(ReleaseNoteClient.class);

    private String releaseNoteServiceUrl;
    private RestTemplate restTemplate;
    private HttpHeaders headers;
    private static final ParameterizedTypeReference<List <LineItem>> LINE_ITEM_LIST_TYPE_REF = new ParameterizedTypeReference<>(){};

    public ReleaseNoteClient(String releaseNoteServiceUrl, String authToken) {
        this.releaseNoteServiceUrl = releaseNoteServiceUrl;
        headers = new HttpHeaders();
        headers.add("Cookie", authToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate = new RestTemplateBuilder()
                .additionalMessageConverters(new GsonHttpMessageConverter())
                .errorHandler(new ExpressiveErrorHandler())
                .build();

        //Add a ClientHttpRequestInterceptor to the RestTemplate to add cookies as required
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().addAll(headers);
            return execution.execute(request, body);
        });
    }

    public List<LineItem> getLineItems(String branchPath) throws RestClientException {
        try {
            ResponseEntity<List<LineItem>> responseEntity = restTemplate.exchange(
                this.releaseNoteServiceUrl + branchPath + "/lineitems",
                    HttpMethod.GET,
                    null,
                    LINE_ITEM_LIST_TYPE_REF);
            return responseEntity.getBody();
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            String errorMessage = String.format("Failed to get line items for branch %s. Error message: %s", branchPath, e.getMessage());
            logger.error(errorMessage);
            throw new RestClientException(errorMessage);
        }
    }

    public void promoteLineItem(String branchPath, String lineItemId)  throws RestClientException {
        try {
            restTemplate.postForObject(this.releaseNoteServiceUrl + branchPath + "/lineitems/" + lineItemId + "/promote", null, Void.class);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            String errorMessage = String.format("Failed to promote line item id= %s for branch %s. Error message: %s", lineItemId, branchPath, e.getMessage());
            logger.error(errorMessage);
            throw new RestClientException(errorMessage);
        }
    }
}

