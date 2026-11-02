package com.tesa.ekowatch.ward.web;

import com.tesa.ekowatch.user.api.ActorAccess;
import com.tesa.ekowatch.ward.api.StreetView;
import com.tesa.ekowatch.ward.api.WardApi;
import com.tesa.ekowatch.ward.api.WardView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wards")
@Tag(name = "Wards & Streets", description = "Ward and street directory lookup")
@SecurityRequirement(name = "bearerAuth")
public class WardController {

    private final WardApi wards;
    private final ActorAccess actors;

    public WardController(WardApi wards, ActorAccess actors) {
        this.wards = wards;
        this.actors = actors;
    }

    @GetMapping
    @Operation(
            operationId = "listWards",
            summary = "List all wards ordered by name",
            description = "Requires `Authorization: Bearer <token>`."
    )
    public List<WardView> listWards(Authentication authentication) {
        return wards.listWards(actors.id(authentication));
    }

    @GetMapping("/{wardId}/streets")
    @Operation(
            operationId = "listStreets",
            summary = "List streets belonging to a ward",
            description = "Requires `Authorization: Bearer <token>`. Returns `404 RESOURCE_NOT_FOUND` if `wardId` does not exist."
    )
    public List<StreetView> listStreets(
            Authentication authentication,
            @PathVariable UUID wardId) {
        return wards.listStreets(actors.id(authentication), wardId);
    }
}
