package org.ihtsdo.authoringservices.configuration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.common.base.Charsets;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriRewriteFilter;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.authoringservices.service.TaskService;
import org.ihtsdo.authoringservices.service.jira.ImpersonatingJiraClientFactory;
import org.ihtsdo.otf.jms.MessagingHelper;
import org.ihtsdo.otf.rest.client.orchestration.OrchestrationRestClient;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

import java.util.TimeZone;

import static com.google.common.base.Predicates.not;
import static springfox.documentation.builders.PathSelectors.regex;

@SpringBootApplication
@EnableCaching
@EnableConfigurationProperties
@EnableJpaRepositories(basePackages = "org.ihtsdo.authoringservices.review")
@EntityScan(basePackages = "org.ihtsdo.authoringservices.review")
public abstract class Configuration {

    @Bean
    public TaskService taskService(@Autowired ImpersonatingJiraClientFactory jiraClientFactory, @Value("${jira.username}") String jiraUsername) throws JiraException {
        return new TaskService(jiraClientFactory, jiraUsername);
    }

    @Bean
    public SnowstormRestClientFactory snowstormRestClientFactory(@Value("${snowstorm.url}") String snowstormUrl) {
        return new SnowstormRestClientFactory(snowstormUrl, null);
    }

    @Bean
    public OrchestrationRestClient orchestrationRestClient(@Value("${orchestration.url}") String orchestrationUrl,
                @Value("${orchestration.username}") String username, @Value("${orchestration.password}") String password) {

        return new OrchestrationRestClient(orchestrationUrl, username, password);
    }

    @Bean
    public ObjectMapper objectMapper() {
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        final ISO8601DateFormat df = new ISO8601DateFormat();
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        objectMapper.setDateFormat(df);
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return objectMapper;
    }

    @Bean
    public MessagingHelper messagingHelper() {
        return new MessagingHelper();
    }

    @Bean
    public HttpMessageConverters customConverters() {
        final StringHttpMessageConverter stringConverter = new StringHttpMessageConverter(Charsets.UTF_8);
        stringConverter.setWriteAcceptCharset(false);

        final MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        jacksonConverter.setObjectMapper(objectMapper());

        return new HttpMessageConverters(
                stringConverter,
                new ByteArrayHttpMessageConverter(),
                new ResourceHttpMessageConverter(),
                jacksonConverter);
    }

    @Bean
    public FilterRegistrationBean getUrlRewriteFilter() {
        // Encode branch paths in uri to allow request mapping to work
        return new FilterRegistrationBean(new BranchPathUriRewriteFilter(
                "/loinc-export/(.*)"
        ));
    }

    // Swagger Config
    @Bean
    public Docket api() {
        return new Docket(DocumentationType.SWAGGER_2)
                .select()
                .apis(RequestHandlerSelectors.any())
                .paths(not(regex("/error")))
                .build();
    }

}
