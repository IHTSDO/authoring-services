package org.ihtsdo.authoringservices.service.client;

import org.ihtsdo.otf.rest.client.ExpressiveErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

public class RVFClient {
    private static final Logger logger = LoggerFactory.getLogger(RVFClient.class);

    private final String rvfUrl;
    private final RestTemplate restTemplate;
    private final RestTemplate multiPartContentTypeRestTemplate;

    public RVFClient(String rvfUrl, String authToken) {
        this.rvfUrl = rvfUrl;

        restTemplate = getNewRestTemplate();
        addInterceptorToRestTemplate(restTemplate, getHeadersForContentType(null, authToken));

        multiPartContentTypeRestTemplate = getNewRestTemplate();
        addInterceptorToRestTemplate(multiPartContentTypeRestTemplate, getHeadersForContentType(MediaType.MULTIPART_FORM_DATA, authToken));
    }

    public URI triggerValidation(MultiValueMap<String, Object> requestBody) throws RestClientException {
        try {
            return this.multiPartContentTypeRestTemplate.postForLocation(this.rvfUrl + "run-post", requestBody);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            String errorMessage = String.format("Failed run validation. Error message: %s", e.getMessage());
            logger.error(errorMessage);
            throw new RestClientException(errorMessage);
        }
    }

    public String getValidationReport(String reportAbsoluteUrl) {
        return restTemplate.getForObject(reportAbsoluteUrl, String.class);
    }

    private RestTemplate getNewRestTemplate() {
        return new RestTemplateBuilder()
                .errorHandler(new ExpressiveErrorHandler())
                .build();
    }

    private HttpHeaders getHeadersForContentType(MediaType type, String authToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Cookie", authToken);
        if (null != type) {
            headers.setContentType(type);
        }
        return headers;
    }

    private void addInterceptorToRestTemplate(RestTemplate restTemplate, HttpHeaders headers) {
        //Add a ClientHttpRequestInterceptor to the RestTemplate to add cookies as required
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().addAll(headers);
            return execution.execute(request, body);
        });
    }
}

