package com.tesa.ekowatch.shared;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String type,
        String title,
        int status,
        String code,
        String detail,
        String instance,
        String requestId,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<FieldError> fieldErrors
) {
    public ApiError(String code, String message, String requestId) {
        this(
                "urn:ekowatch:problem:" + (code == null ? "internal-error" : code.toLowerCase().replace('_', '-')),
                code,
                500,
                code,
                message,
                null,
                requestId,
                null
        );
    }

    public String message() {
        return detail;
    }

    public record FieldError(String field, String message) {
    }
}
