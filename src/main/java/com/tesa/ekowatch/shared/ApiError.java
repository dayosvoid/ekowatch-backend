package com.tesa.ekowatch.shared;

public record ApiError(
        String code,
        String message,
        String requestId
) {
}