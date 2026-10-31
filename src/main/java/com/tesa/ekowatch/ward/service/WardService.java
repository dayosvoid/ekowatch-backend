package com.tesa.ekowatch.ward.service;
import com.tesa.ekowatch.ward.api.*;
import com.tesa.ekowatch.user.api.ActorAccess;
import com.tesa.ekowatch.shared.Location;
import com.tesa.ekowatch.shared.RuleViolation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class WardService implements WardApi {
    private final JdbcTemplate db;
    private final ActorAccess actors;
    public WardService(JdbcTemplate db, ActorAccess actors) { this.db = db; this.actors = actors; }

    public List<WardView> listWards(UUID actorId) {
        actors.requireUser(actorId);
        return db.query("select id,name from wards order by name,id",
                (r,n) -> new WardView(r.getObject("id",UUID.class),r.getString("name")));
    }

    public List<StreetView> listStreets(UUID actorId, UUID wardId) {
        actors.requireUser(actorId);
        if (wardId == null) throw new RuleViolation("VALIDATION_FAILED");
        if (!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from wards where id=?)", Boolean.class, wardId)))
            throw new RuleViolation("RESOURCE_NOT_FOUND");
        return db.query("select id,ward_id,name,latitude,longitude from streets where ward_id=? order by name,id",
                (r,n) -> new StreetView(r.getObject("id",UUID.class),r.getObject("ward_id",UUID.class),r.getString("name"),
                        r.getObject("latitude") == null ? null : new Location(r.getDouble("latitude"),r.getDouble("longitude"))), wardId);
    }
}
