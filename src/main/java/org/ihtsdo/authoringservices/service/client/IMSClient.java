package org.ihtsdo.authoringservices.service.client;

import org.ihtsdo.authoringservices.domain.User;
import org.ihtsdo.otf.rest.client.ExpressiveErrorHandler;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IMSClient {

    private RestTemplate restTemplate;
    private HttpHeaders headers;

    public static final String ROLE_PREFIX = "ROLE_";

    private static final ParameterizedTypeReference<List<User>> USER_LIST_TYPE_REF = new ParameterizedTypeReference<>() {
    };

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

    public User getLoggedInAccount() {
        Map<String, String> params = new HashMap<>();
        User user = restTemplate.getForObject("/account", User.class, params);
        if (user != null && user.getRoles() != null) {
            List<String> roles = new ArrayList<>();
            user.getRoles().forEach(item -> roles.add(item.replace(ROLE_PREFIX, "")));
            user.setRoles(roles);
        }
        return user;
    }

    public List<User> searchUserByGroupname(String groupname, int startAt, int maxResults) {
        Map<String, String> params = new HashMap<>();
        params.put("groupname", groupname);
        params.put("startAt", String.valueOf(startAt));
        params.put("maxResults", String.valueOf(maxResults));
        ResponseEntity<List<User>> responseEntity =  restTemplate.exchange("/group/user?groupname={groupname}&startAt={startAt}&maxResults={maxResults}", HttpMethod.GET, null, USER_LIST_TYPE_REF, params);
        return responseEntity.getBody();
    }
}
