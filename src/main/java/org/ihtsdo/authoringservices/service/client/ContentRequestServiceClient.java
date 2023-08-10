package org.ihtsdo.authoringservices.service.client;

import org.ihtsdo.otf.rest.client.ExpressiveErrorHandler;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

public class ContentRequestServiceClient {
    private static final Logger logger = LoggerFactory.getLogger(ContentRequestServiceClient.class);

    private String contentRequestServiceUrl;
    private RestTemplate restTemplate;
    private HttpHeaders headers;

    public ContentRequestServiceClient(String contentRequestServiceUrl, String authToken) {
        this.contentRequestServiceUrl = contentRequestServiceUrl;
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

    public String createRequest(Object object) throws RestClientException {
        String requestId;
        try {
            ContentRequestDto response = restTemplate.postForObject(this.contentRequestServiceUrl + "request", object, ContentRequestDto.class);
            requestId = response.getId().toString();
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            String errorMessage = String.format("Failed to create new CRS request. Error message: %s", e.getMessage());
            logger.error(errorMessage);
            throw new RestClientException(errorMessage);
        }

        return requestId;
    }


    private class ContentRequestDto {
        private Long id;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }
}

