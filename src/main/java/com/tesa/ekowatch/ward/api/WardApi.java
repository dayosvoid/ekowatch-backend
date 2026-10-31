package com.tesa.ekowatch.ward.api;
import java.util.List;
import java.util.UUID;
/** actorId is private, supplied by a trusted principal, never client JSON. */
public interface WardApi {
    List<WardView> listWards(UUID actorId);
    List<StreetView> listStreets(UUID actorId, UUID wardId);
}
