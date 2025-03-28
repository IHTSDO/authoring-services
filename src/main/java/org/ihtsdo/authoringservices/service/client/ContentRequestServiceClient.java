package org.ihtsdo.authoringservices.service.client;

import net.sf.json.JSONObject;
import org.ihtsdo.otf.rest.client.ExpressiveErrorHandler;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

public class ContentRequestServiceClient {
    private static final Logger logger = LoggerFactory.getLogger(ContentRequestServiceClient.class);
    public static final String REQUEST_ENDPOINT = "ihtsdo-crs/api/request";

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
            ContentRequestDto response = restTemplate.postForObject(this.contentRequestServiceUrl + REQUEST_ENDPOINT, object, ContentRequestDto.class);
            if (response != null) {
                requestId = String.valueOf(response.getId());
            } else {
                throw new RestClientException("Failed to create new CRS request");
            }
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            String errorMessage = String.format("Failed to create new CRS request. Error message: %s", e.getMessage());
            logger.error(errorMessage);
            throw new RestClientException(errorMessage);
        }

        return requestId;
    }

    public ContentRequestDto getRequestDetails(String requestId) {
        return restTemplate.getForObject(this.contentRequestServiceUrl + REQUEST_ENDPOINT + "/" + requestId, ContentRequestDto.class);
    }

    public List<Long> findRequestsByAuthoringTask(String taskKey) {
        JSONObject body = new JSONObject();
        body.put("authoringTaskTicket", taskKey);
        body.put("sortDirections", List.of("desc"));
        body.put("sortFields", List.of("id"));
        body.put("limit", 1000);
        ContentRequestSearchResponseDto contentRequestSearchResponseDto = restTemplate.postForObject(this.contentRequestServiceUrl + REQUEST_ENDPOINT + "/list", body, ContentRequestSearchResponseDto.class);
        if (contentRequestSearchResponseDto != null) {
            return contentRequestSearchResponseDto.getItems().stream().map(ContentRequestDto::getId).toList();
        }
        return Collections.emptyList();
    }

    public class ContentRequestSearchResponseDto {
        private List<ContentRequestDto> items;

        public List<ContentRequestDto> getItems() {
            return items;
        }

        public void setItems(List<ContentRequestDto> items) {
            this.items = items;
        }
    }

    public class ContentRequestDto {
        private Long id;

        private RequestHeader requestHeader;

        private JSONObject concept;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public RequestHeader getRequestHeader() {
            return requestHeader;
        }

        public void setRequestHeader(RequestHeader requestHeader) {
            this.requestHeader = requestHeader;
        }

        public void setConcept(JSONObject concept) {
            this.concept = concept;
        }

        public JSONObject getConcept() {
            return concept;
        }
    }

    public class RequestHeader {
        private String organization;

        public void setOrganization(String organization) {
            this.organization = organization;
        }

        public String getOrganization() {
            return organization;
        }
    }
}

