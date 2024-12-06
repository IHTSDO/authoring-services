package org.ihtsdo.authoringservices.configuration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import io.kaicode.rest.util.branchpathrewrite.BranchPathUriRewriteFilter;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import jakarta.jms.ConnectionFactory;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.authoringservices.service.ProjectService;
import org.ihtsdo.authoringservices.service.TaskService;
import org.ihtsdo.authoringservices.service.impl.AuthoringProjectServiceImpl;
import org.ihtsdo.authoringservices.service.impl.JiraProjectServiceImpl;
import org.ihtsdo.authoringservices.service.impl.JiraTaskServiceImpl;
import org.ihtsdo.authoringservices.service.jira.ImpersonatingJiraClientFactory;
import org.ihtsdo.otf.jms.MessagingHelper;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;

import java.nio.charset.StandardCharsets;
import java.util.TimeZone;

@SpringBootApplication
@EnableCaching
@EnableConfigurationProperties
@EnableJpaRepositories(basePackages = "org.ihtsdo.authoringservices.repository")
@EntityScan(basePackages = "org.ihtsdo.authoringservices.entity")
public abstract class Configuration {
	@Autowired(required = false)
	private BuildProperties buildProperties;

	@Autowired
	private ConnectionFactory connectionFactory;

	@Bean
	public TaskService taskService(@Autowired @Qualifier("authoringTaskOAuthJiraClient") ImpersonatingJiraClientFactory jiraClientFactory, @Value("${jira.username}") String jiraUsername) throws JiraException {
        return new JiraTaskServiceImpl(jiraClientFactory, jiraUsername);
	}

	@Bean
	@Primary
	public ProjectService projectService(@Autowired @Qualifier("authoringTaskOAuthJiraClient") ImpersonatingJiraClientFactory jiraClientFactory, @Value("${jira.username}") String jiraUsername) throws JiraException {
		return new JiraProjectServiceImpl(jiraClientFactory, jiraUsername);
	}

	@Bean(name = "authoringProjectService")
	public ProjectService authoringProjectService() {
		return new AuthoringProjectServiceImpl();
	}

	@Bean
	public SnowstormRestClientFactory snowstormRestClientFactory(@Value("${snowstorm.url}") String snowstormUrl) {
		return new SnowstormRestClientFactory(snowstormUrl, null);
	}

	@Bean
	public ObjectMapper objectMapper() {
		final ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
		final StdDateFormat stdDateFormat = new StdDateFormat();
		stdDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
		objectMapper.setDateFormat(stdDateFormat);
		objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		return objectMapper;
	}

	@Bean
	public MessagingHelper messagingHelper() {
		return new MessagingHelper();
	}

	@Bean(name = "topicJmsListenerContainerFactory")
	public DefaultJmsListenerContainerFactory getTopicFactory() {
		DefaultJmsListenerContainerFactory factory = new  DefaultJmsListenerContainerFactory();
		factory.setConnectionFactory(connectionFactory);
		factory.setSessionTransacted(true);
		factory.setPubSubDomain(true);
		return factory;
	}

	@Bean
	public HttpMessageConverters customConverters() {
		final StringHttpMessageConverter stringConverter = new StringHttpMessageConverter(StandardCharsets.UTF_8);
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
				"/loinc-export/(.*)",
				"/validation/(.*)/status/reset",
				"/branches/(.*)/validation",
				"/branches/(.*)/classifications",
				"/branches/(.*)/validation-reports/(.*)",
				"/branches/(.*)/authoring-info"
		));
	}

	@Bean
	public GroupedOpenApi apiDocs() {
		return GroupedOpenApi.builder()
				.group("authoring-services")
				.packagesToScan("org.ihtsdo.authoringservices.rest")
				// Don't show the error or root endpoints in Swagger
				.pathsToExclude("/error", "/")
				.build();
	}

	@Bean
	public GroupedOpenApi springActuatorApi() {
		return GroupedOpenApi.builder()
				.group("actuator")
				.packagesToScan("org.springframework.boot.actuate")
				.pathsToMatch("/actuator/**")
				.build();
	}

	@Bean
	public OpenAPI apiInfo() {
		final String version = buildProperties != null ? buildProperties.getVersion() : "DEV";
		return new OpenAPI()
				.info(new Info()
						.title("SNOMED CT Authoring Services")
						.description("Authoring Services is a component of the SNOMED CT Authoring Platform")
						.version(version)
						.contact(new Contact().name("SNOMED International").url("https://www.snomed.org"))
						.license(new License().name("Apache 2.0").url("http://www.apache.org/licenses/LICENSE-2.0")))
				.externalDocs(new ExternalDocumentation()
						.description("See more about Authoring Services in GitHub")
						.url("https://github.com/IHTSDO/authoring-services"));
	}

}
