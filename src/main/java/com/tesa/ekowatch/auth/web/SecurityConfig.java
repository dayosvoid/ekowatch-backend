package com.tesa.ekowatch.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tesa.ekowatch.auth.service.ApiUserDetailsService;
import com.tesa.ekowatch.auth.service.BearerTokenService;
import com.tesa.ekowatch.shared.ApiError;
import com.tesa.ekowatch.shared.ApiExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final ApiUserDetailsService userDetailsService;
    private final ObjectProvider<BearerTokenService> tokenServiceProvider;
    private final ObjectProvider<ObjectMapper> objectMapperProvider;
    private final String allowedOriginsProperty;

    public SecurityConfig(
            ApiUserDetailsService userDetailsService,
            ObjectProvider<BearerTokenService> tokenServiceProvider,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            @Value("${ekowatch.cors.allowed-origins:https://eko-watch--cflk38sh3.qocent.app,http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000,http://localhost:4173,https://*.qocent.app,https://*.qocent.com,https://*.vercel.app,https://*.netlify.app,https://*.onrender.com}")
            String allowedOriginsProperty) {
        this.userDetailsService = userDetailsService;
        this.tokenServiceProvider = tokenServiceProvider;
        this.objectMapperProvider = objectMapperProvider;
        this.allowedOriginsProperty = allowedOriginsProperty;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> originPatterns = Arrays.stream(allowedOriginsProperty.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        config.setAllowedOriginPatterns(originPatterns);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "Idempotency-Key",
                "X-Request-Id",
                "X-Request-ID",
                "X-Requested-With",
                "Cache-Control"
        ));
        config.setExposedHeaders(List.of(
                "Location",
                "X-Request-Id",
                "X-Request-ID"
        ));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
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
                .cors(Customizer.withDefaults())
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
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/sessions", "/api/v1/auth/login").permitAll()
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
