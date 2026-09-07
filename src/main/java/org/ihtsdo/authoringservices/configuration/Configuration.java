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
import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.ihtsdo.authoringservices.service.ProjectService;
import org.ihtsdo.authoringservices.service.TaskService;
import org.ihtsdo.authoringservices.service.client.JiraCloudClient;
import org.ihtsdo.authoringservices.service.impl.*;
import org.ihtsdo.authoringservices.service.jira.ImpersonatingJiraClientFactory;
import org.ihtsdo.otf.jms.MessagingHelper;
import org.ihtsdo.otf.rest.client.terminologyserver.SnowstormRestClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters.ServerBuilder;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.TimeZone;

@SpringBootApplication
@EnableCaching
@EnableConfigurationProperties
@EnableJpaRepositories(basePackages = "org.ihtsdo.authoringservices.repository")
@EntityScan(basePackages = "org.ihtsdo.authoringservices.entity")
public abstract class Configuration {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired(required = false)
	private BuildProperties buildProperties;

	@Autowired
	private ConnectionFactory connectionFactory;

	@Bean
	public JiraCloudClient jiraCloudClient(@Value("${jira.cloud.base-url}") String baseUrl, @Value("${jira.cloud.username}") String email, @Value("${jira.cloud.api-token}") String apiToken, @Value("${jira.cloud.service.desk.id}") String serviceDeskId, @Value("${jira.cloud.service.desk.request-type.id}") String serviceDeskRequestTypeId, @Value("${jira.cloud.service.desk.customFields.country}") String serviceDeskCountryCustomFieldId) {
		return new JiraCloudClient(baseUrl, email, apiToken, serviceDeskId, serviceDeskRequestTypeId, serviceDeskCountryCustomFieldId);
	}

	@Bean
	@Primary
	public TaskService taskService(@Autowired @Qualifier("authoringTaskOAuthJiraClient") ImpersonatingJiraClientFactory jiraClientFactory, @Value("${jira.username}") String jiraUsername, @Value("${jira.enabled}") boolean jiraEnabled) throws JiraException {
        return new JiraTaskServiceImpl(jiraClientFactory, jiraUsername, jiraEnabled);
	}

	@Bean(name = "authoringTaskService")
	public TaskService authoringTaskService() {
		return new AuthoringTaskServiceImpl();
	}

	@Bean(name = "defaultTaskService")
	public TaskService defaultTaskService() {
		return new DefaultTaskServiceImpl();
	}

	@Bean
	@Primary
	public ProjectService projectService(@Autowired @Qualifier("authoringTaskOAuthJiraClient") ImpersonatingJiraClientFactory jiraClientFactory, @Value("${jira.username}") String jiraUsername, @Value("${jira.enabled}") boolean jiraEnabled) throws JiraException {
		return new JiraProjectServiceImpl(jiraClientFactory, jiraUsername, jiraEnabled);
	}

	@Bean(name = "authoringProjectService")
	public ProjectService authoringProjectService() {
		return new AuthoringProjectServiceImpl();
	}

	@Bean(name = "defaultProjectService")
	public ProjectService defaultProjectService() {
		return new DefaultProjectServiceImpl();
	}

	@Bean
	public SnowstormRestClientFactory snowstormRestClientFactory(@Value("${snowstorm.url}") String snowstormUrl) {
		return new SnowstormRestClientFactory(snowstormUrl, null);
	}

	@Bean
	public ObjectMapper objectMapper() {
		final ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_EMPTY);
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
	public CustomHttpMessageConvertersCustomizer customConverters() {
		final StringHttpMessageConverter stringConverter = new StringHttpMessageConverter(StandardCharsets.UTF_8);
		stringConverter.setWriteAcceptCharset(false);

		final tools.jackson.databind.util.StdDateFormat stdDateFormat = new tools.jackson.databind.util.StdDateFormat();
		stdDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
		final JacksonJsonHttpMessageConverter jacksonConverter = new JacksonJsonHttpMessageConverter(
				JsonMapper.builderWithJackson2Defaults()
						.changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_EMPTY))
						.defaultDateFormat(stdDateFormat)
						.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
						.build());

		return new CustomHttpMessageConvertersCustomizer(
				stringConverter,
				new ByteArrayHttpMessageConverter(),
				new ResourceHttpMessageConverter(),
				jacksonConverter);
	}

	/**
	 * Server-only: {@code SnowstormRestClient} and similar code use {@code new RestTemplate()},
	 * which does not pick up Boot client converter customizers.
	 */
	public static final class CustomHttpMessageConvertersCustomizer implements ServerHttpMessageConvertersCustomizer {

		private final StringHttpMessageConverter stringConverter;
		private final ByteArrayHttpMessageConverter byteArrayConverter;
		private final ResourceHttpMessageConverter resourceConverter;
		private final JacksonJsonHttpMessageConverter jacksonConverter;

		private CustomHttpMessageConvertersCustomizer(
				StringHttpMessageConverter stringConverter,
				ByteArrayHttpMessageConverter byteArrayConverter,
				ResourceHttpMessageConverter resourceConverter,
				JacksonJsonHttpMessageConverter jacksonConverter) {
			this.stringConverter = stringConverter;
			this.byteArrayConverter = byteArrayConverter;
			this.resourceConverter = resourceConverter;
			this.jacksonConverter = jacksonConverter;
		}

		@Override
		public void customize(ServerBuilder builder) {
			builder.addCustomConverter(stringConverter)
					.addCustomConverter(byteArrayConverter)
					.addCustomConverter(resourceConverter)
					.withJsonConverter(jacksonConverter);
		}
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
				"/branches/(.*)/authoring-info",
				"/branches/(.*)/language-refsets"
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

	@Bean
	public TomcatConnectorCustomizer connectorCustomizer() {
		// Swagger encodes the slash in branch paths
		logger.info("Configuring Tomcat to decode encoded slashes.");
		return connector -> {
			connector.setEncodedSolidusHandling(EncodedSolidusHandling.DECODE.getValue());
			connector.setAsyncTimeout(0);
		};
	}

}
