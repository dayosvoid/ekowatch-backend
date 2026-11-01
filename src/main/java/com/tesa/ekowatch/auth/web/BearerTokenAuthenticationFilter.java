package com.tesa.ekowatch.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tesa.ekowatch.auth.service.BearerTokenService;
import com.tesa.ekowatch.shared.ApiError;
import com.tesa.ekowatch.shared.ApiExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private final BearerTokenService tokenService;
    private final ObjectMapper objectMapper;

    public BearerTokenAuthenticationFilter(BearerTokenService tokenService, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String rawToken = header.substring(7).trim();
            Optional<BearerTokenService.TokenClaims> claims = tokenService.verify(rawToken);
            if (claims.isEmpty()) {
                SecurityContextHolder.clearContext();
                writeUnauthenticated(request, response);
                return;
            }
            BearerTokenService.TokenClaims verified = claims.get();
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    verified.email(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + verified.role()))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthenticated(HttpServletRequest request, HttpServletResponse response) throws IOException {
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
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
