package com.tesa.ekowatch.auth.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ApiUserDetailsService implements UserDetailsService {

    private final JdbcTemplate db;

    public ApiUserDetailsService(JdbcTemplate db) {
        this.db = db;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        Map<String, Object> user;

        try {
            user = db.queryForMap(
                    """
                    SELECT email, password_hash, role
                    FROM users
                    WHERE email = ?
                    """,
                    email
            );
        } catch (Exception ex) {
            throw new UsernameNotFoundException(
                    "User not found: " + email
            );
        }

        String userEmail = (String) user.get("email");
        String passwordHash = (String) user.get("password_hash");
        String role = (String) user.get("role");

        GrantedAuthority authority =
                new SimpleGrantedAuthority("ROLE_" + role);

        return User.withUsername(userEmail)
                .password(passwordHash)
                .authorities(List.of(authority))
                .build();
    }
}