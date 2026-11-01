package com.tesa.ekowatch.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tesa.ekowatch.auth.service.ApiUserDetailsService;
import com.tesa.ekowatch.auth.service.BearerTokenService;
import com.tesa.ekowatch.shared.ApiError;
import com.tesa.ekowatch.shared.ApiExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final ApiUserDetailsService userDetailsService;
    private final ObjectProvider<BearerTokenService> tokenServiceProvider;
    private final ObjectProvider<ObjectMapper> objectMapperProvider;

    public SecurityConfig(
            ApiUserDetailsService userDetailsService,
            ObjectProvider<BearerTokenService> tokenServiceProvider,
            ObjectProvider<ObjectMapper> objectMapperProvider) {
        this.userDetailsService = userDetailsService;
        this.tokenServiceProvider = tokenServiceProvider;
        this.objectMapperProvider = objectMapperProvider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        ObjectMapper mapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        BearerTokenService tokenService = tokenServiceProvider.getIfAvailable();

        AuthenticationEntryPoint entryPoint = (request, response, authException) -> {
            ApiError problem = ApiExceptionHandler.buildProblem(
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHENTICATED",
                    null,
                    request,
                    null
            );
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setHeader("X-Request-Id", problem.requestId());
            mapper.writeValue(response.getOutputStream(), problem);
        };

        AccessDeniedHandler accessDeniedHandler = (request, response, accessDeniedException) -> {
            ApiError problem = ApiExceptionHandler.buildProblem(
                    HttpStatus.FORBIDDEN,
                    "FORBIDDEN",
                    null,
                    request,
                    null
            );
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setHeader("X-Request-Id", problem.requestId());
            mapper.writeValue(response.getOutputStream(), problem);
        };

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .userDetailsService(userDetailsService)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/sessions").permitAll()
                        .requestMatchers(
                                "/actuator/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(httpBasic -> httpBasic.authenticationEntryPoint(entryPoint));

        if (tokenService != null) {
            http.addFilterBefore(
                    new BearerTokenAuthenticationFilter(tokenService, mapper),
                    UsernamePasswordAuthenticationFilter.class
            );
        }

        return http.build();
    }
}
