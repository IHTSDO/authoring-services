package org.ihtsdo.authoringservices.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsByNameServiceWrapper;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

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
			"/swagger-ui/**",
			"/v3/api-docs/**"
	};

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationManager authManager) throws Exception {

		http.csrf(AbstractHttpConfigurer::disable);

		RequestHeaderAuthenticationFilter preAuthFilter = new RequestHeaderAuthenticationFilter();
		preAuthFilter.setAuthenticationManager(authManager);

		// header names from your base class
		preAuthFilter.setPrincipalRequestHeader("X-AUTH-username");
		preAuthFilter.setCredentialsRequestHeader("X-AUTH-token");
		preAuthFilter.setExceptionIfHeaderMissing(false);

		http.addFilterBefore(preAuthFilter, AnonymousAuthenticationFilter.class);

		for (String excludedPath : excludedUrlPatterns) {
			http.authorizeHttpRequests(auth ->
					auth.requestMatchers(new AntPathRequestMatcher(excludedPath)).permitAll());
		}

		if (requiredRole != null && !requiredRole.isEmpty()) {
			http.authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority(requiredRole));
		} else {
			http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
		}

		return http.build();
	}

	@Bean
	public AuthenticationManager authenticationManager(UserDetailsService userDetailsService) {
		PreAuthenticatedAuthenticationProvider provider = new PreAuthenticatedAuthenticationProvider();
		provider.setPreAuthenticatedUserDetailsService(
				new UserDetailsByNameServiceWrapper<>(userDetailsService)
		);
		return new ProviderManager(provider);
	}

	@Bean
	public UserDetailsService userDetailsService() {
		return username -> {
			String effectiveUsername = overrideUsername != null && !overrideUsername.isBlank()
					? overrideUsername : username;

			String roles = overrideRoles != null && !overrideRoles.isBlank()
					? overrideRoles : "ROLE_USER";

			return org.springframework.security.core.userdetails.User
					.withUsername(effectiveUsername)
					.password("") // not used for pre-auth
					.authorities(roles.split(","))
					.accountExpired(false)
					.accountLocked(false)
					.credentialsExpired(false)
					.disabled(false)
					.build();
		};
	}
}
