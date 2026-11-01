package com.tesa.ekowatch.ward.web;

import com.tesa.ekowatch.user.api.ActorAccess;
import com.tesa.ekowatch.ward.api.StreetView;
import com.tesa.ekowatch.ward.api.WardApi;
import com.tesa.ekowatch.ward.api.WardView;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wards")
public class WardController {

    private final WardApi wards;
    private final ActorAccess actors;

    public WardController(WardApi wards, ActorAccess actors) {
        this.wards = wards;
        this.actors = actors;
    }

    @GetMapping
    public List<WardView> listWards(Authentication authentication) {
        return wards.listWards(actors.id(authentication));
    }

    @GetMapping("/{wardId}/streets")
    public List<StreetView> listStreets(
            Authentication authentication,
            @PathVariable UUID wardId) {
        return wards.listStreets(actors.id(authentication), wardId);
    }
}
