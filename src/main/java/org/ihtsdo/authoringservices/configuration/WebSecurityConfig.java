package org.ihtsdo.authoringservices.configuration;

import org.ihtsdo.authoringservices.rest.security.RequestHeaderAuthenticationDecoratorWithOverride;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

	@Value("${ims-security.required-role}")
	private String requiredRole;

	@Value("${authentication.override.username}")
	private String overrideUsername;

	@Value("${authentication.override.roles}")
	private String overrideRoles;

	@Value("${authentication.override.token}")
	private String overrideToken;

	private final String[] excludedUrlPatterns = {
			"/",
			"/version",
			"/ui-configuration",
			"/authoring-services-websocket/**/*",
			// Swagger API Docs:
			"/swagger-ui/**",
			"/v3/api-docs/**"
	};

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.httpBasic(withDefaults());
		http.csrf(AbstractHttpConfigurer::disable);
		http.sessionManagement(sessionManagement -> sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		http.addFilterBefore(new RequestHeaderAuthenticationDecoratorWithOverride(overrideUsername, overrideRoles, overrideToken), AuthorizationFilter.class);
		if (requiredRole != null && !requiredRole.isEmpty()) {
			http.authorizeHttpRequests((authorize) -> authorize.requestMatchers(excludedUrlPatterns)
					.permitAll()
					.anyRequest().hasAuthority(requiredRole)
			);

		} else {
			http.authorizeHttpRequests((authorize) -> authorize.requestMatchers(excludedUrlPatterns)
					.permitAll()
					.anyRequest().authenticated()
			);
		}

		return http.build();
	}
}
