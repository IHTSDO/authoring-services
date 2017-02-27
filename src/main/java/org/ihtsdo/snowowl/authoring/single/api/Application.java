package org.ihtsdo.snowowl.authoring.single.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.ISO8601DateFormat;
import com.google.common.base.Charsets;
import org.ihtsdo.snowowl.authoring.single.api.service.restclient.SnowOwlRestClientFactory;
import org.ihtsdo.sso.integration.RequestHeaderAuthenticationDecorator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.HttpMessageConverters;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportResource;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jms.annotation.EnableJms;

import java.util.TimeZone;

@SpringBootApplication
@ImportResource("classpath:services-context.xml")
@EnableJms
public class Application {

	@Bean
	public SnowOwlRestClientFactory snowOwlRestClientFactory(@Value("${snowowl.url}") String snowOwlUrl) {
		return new SnowOwlRestClientFactory(snowOwlUrl);
	}

	@Bean
	public FilterRegistrationBean getSingleSignOnFilter() {
		return new FilterRegistrationBean(new RequestHeaderAuthenticationDecorator());
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

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);

	}
}
