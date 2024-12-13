package org.ihtsdo.authoringservices.service.client;

import org.ihtsdo.authoringservices.domain.User;
import org.ihtsdo.otf.rest.client.ExpressiveErrorHandler;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

public class IMSClient {

    private RestTemplate restTemplate;
    private HttpHeaders headers;

    public IMSClient(String imsUrl, String authToken) {
        headers = new HttpHeaders();
        headers.add("Cookie", authToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate = new RestTemplateBuilder()
                .additionalMessageConverters(new GsonHttpMessageConverter())
                .errorHandler(new ExpressiveErrorHandler())
                .rootUri(imsUrl)
                .build();

        //Add a ClientHttpRequestInterceptor to the RestTemplate to add cookies as required
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().addAll(headers);
            return execution.execute(request, body);
        });
    }

    public User getUserDetails(String username) {
        Map<String, String> params = new HashMap<>();
        params.put("username", username);
        return restTemplate.getForObject("/user?username={username}", User.class, params);
    }
}
