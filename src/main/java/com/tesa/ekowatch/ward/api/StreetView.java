package com.tesa.ekowatch.ward.api;
import com.tesa.ekowatch.shared.Location;
import java.util.UUID;
public record StreetView(UUID streetId, UUID wardId, String streetName, Location mapLocation) {}
