package com.tesa.ekowatch.collection.api;
import com.tesa.ekowatch.ward.api.StreetView;
import java.time.LocalDate;
import java.util.*;
/** actorId is private trusted-principal input, never client JSON. */
public interface CollectionApi {
    List<CollectionScheduleView> listCollectionSchedules(UUID actorId, UUID wardId, LocalDate fromDate, LocalDate toDate);
    List<StreetView> listMyAssignedStreets(UUID actorId, LocalDate fromDate, LocalDate toDate);
    CollectionView recordCollection(UUID actorId, UUID streetId, LocalDate collectionDate);
}