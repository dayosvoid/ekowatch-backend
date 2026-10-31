package com.tesa.ekowatch.user.service;
import com.tesa.ekowatch.user.api.*;
import com.tesa.ekowatch.shared.RuleViolation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserService implements UserApi {
    private final JdbcTemplate db;
    private final ActorAccess actors;

    public UserService(JdbcTemplate db, ActorAccess actors) { this.db = db; this.actors = actors; }

    public CurrentUserView getCurrentUser(UUID actorId) {
        actors.requireUser(actorId);
        return db.queryForObject("select id,email,display_name,role,ward_id,street_id from users where id=?",
                (r,n) -> new CurrentUserView(r.getObject("id", UUID.class), r.getString("email"),
                        r.getString("display_name"), Role.valueOf(r.getString("role")),
                        r.getObject("ward_id", UUID.class), r.getObject("street_id", UUID.class)), actorId);
    }

    @Transactional
    public CurrentUserView updateMyPreferences(UUID actorId, UUID wardId, UUID streetId) {
        actors.requireRole(actorId, "RESIDENT");
        if (wardId == null || streetId == null) throw new RuleViolation("VALIDATION_FAILED");
        if (!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from streets where id=? and ward_id=?)",
                Boolean.class, streetId, wardId))) throw new RuleViolation("VALIDATION_FAILED");
        db.update("update users set ward_id=?,street_id=? where id=?", wardId, streetId, actorId);
        return getCurrentUser(actorId);
    }
}
