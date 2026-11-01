package com.tesa.ekowatch.authority.web;

import com.tesa.ekowatch.authority.api.AuthorityApi;
import com.tesa.ekowatch.authority.api.WardDashboardView;
import com.tesa.ekowatch.user.api.ActorAccess;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wards")
public class AuthorityController {

    private final AuthorityApi authorities;
    private final ActorAccess actors;

    public AuthorityController(AuthorityApi authorities, ActorAccess actors) {
        this.authorities = authorities;
        this.actors = actors;
    }

    @GetMapping("/{wardId}/dashboard")
    public WardDashboardView getWardDashboard(
            Authentication authentication,
            @PathVariable UUID wardId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        return authorities.getWardDashboard(
                actors.id(authentication),
                wardId,
                fromDate,
                toDate
        );
    }
}
