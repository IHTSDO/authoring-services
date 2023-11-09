package org.ihtsdo.authoringservices.configuration;

import org.ihtsdo.authoringservices.rest.security.RequestHeaderAuthenticationDecoratorWithOverride;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

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
		http.csrf(AbstractHttpConfigurer::disable);
		http.addFilterBefore(new RequestHeaderAuthenticationDecoratorWithOverride(overrideUsername, overrideRoles, overrideToken), AuthorizationFilter.class);
		for (String excludedPath : excludedUrlPatterns) {
			http.authorizeHttpRequests(auth -> auth.requestMatchers(new AntPathRequestMatcher(excludedPath)).permitAll());
		}
		if (requiredRole != null && !requiredRole.isEmpty()) {
			http.authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority(requiredRole)).httpBasic(withDefaults());
		} else {
			http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated()).httpBasic(withDefaults());
		}
		http.securityContext((securityContext) -> securityContext
				.requireExplicitSave(false));
		return http.build();
	}
}
