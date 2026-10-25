package com.tesa.ekowatch.user.service;

import com.tesa.ekowatch.user.api.ActorAccess;
import com.tesa.ekowatch.shared.RuleViolation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DatabaseActorAccess implements ActorAccess {
    private final JdbcTemplate db;

    public DatabaseActorAccess(JdbcTemplate db) {
        this.db = db;
    }

    public void requireRole(UUID id, String role) {
        if (id == null || !Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from users where id=? and role=?)", Boolean.class, id, role)))
            throw new RuleViolation("FORBIDDEN");
    }

    public void requireUser(UUID id) {
        if (id == null || !Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from users where id=?)", Boolean.class, id)))
            throw new RuleViolation("FORBIDDEN");
    }
}
