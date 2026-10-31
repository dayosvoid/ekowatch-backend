package com.tesa.ekowatch.shared;
import java.util.List;
public record Page<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    public Page { items = List.copyOf(items); }
}
