package com.tesa.ekowatch.shared;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(RuleViolation.class)
    public ResponseEntity<ApiError> handleRuleViolation(
            RuleViolation exception,
            HttpServletRequest request) {

        String code = exception.code();

        HttpStatus status = switch (code) {
            case "FORBIDDEN", "WARD_ASSIGNMENT_REQUIRED" -> HttpStatus.FORBIDDEN;

            case "REPORT_NOT_FOUND", "RESOURCE_NOT_FOUND" ->
                    HttpStatus.NOT_FOUND;

            case "DUPLICATE_CONFIRMATION",
                 "DUPLICATE_PICKUP",
                 "REPORT_NOT_ELIGIBLE",
                 "SELF_CONFIRMATION_NOT_ALLOWED" ->
                    HttpStatus.CONFLICT;

            case "VALIDATION_FAILED", "INVALID_REPORT",
                 "INVALID_LOCATION",
                 "INVALID_PAGE",
                 "INVALID_PICKUP",
                 "INVALID_DATE_RANGE" ->
                    HttpStatus.BAD_REQUEST;

            default ->
                    HttpStatus.INTERNAL_SERVER_ERROR;
        };

        return ResponseEntity
                .status(status)
                .body(error(
                        code,
                        messageFor(code),
                        request
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        return ResponseEntity
                .badRequest()
                .body(error(
                        "VALIDATION_ERROR",
                        "Request validation failed",
                        request
                ));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiError> handleMalformedRequest(
            Exception exception,
            HttpServletRequest request) {

        return ResponseEntity
                .badRequest()
                .body(error(
                        "INVALID_REQUEST",
                        "Malformed request",
                        request
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error(
                        "INTERNAL_ERROR",
                        "An unexpected error occurred",
                        request
                ));
    }

    private ApiError error(
            String code,
            String message,
            HttpServletRequest request) {

        String requestId = request.getHeader("X-Request-Id");

        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        return new ApiError(
                code,
                message,
                requestId
        );
    }

    private String messageFor(String code) {
        return switch (code) {
            case "FORBIDDEN", "WARD_ASSIGNMENT_REQUIRED" ->
                    "You are not authorized to perform this action";

            case "REPORT_NOT_FOUND", "RESOURCE_NOT_FOUND" ->
                    "Report was not found";

            case "DUPLICATE_CONFIRMATION" ->
                    "You have already confirmed this report";

            case "DUPLICATE_PICKUP" ->
                    "A pickup has already been recorded for this street and date";

            case "REPORT_NOT_ELIGIBLE" ->
                    "This report cannot be confirmed";

            case "SELF_CONFIRMATION_NOT_ALLOWED" ->
                    "You cannot confirm your own report";

            case "VALIDATION_FAILED", "INVALID_REPORT" ->
                    "Invalid report";

            case "INVALID_LOCATION" ->
                    "Invalid report location";

            case "INVALID_PAGE" ->
                    "Invalid pagination values";

            case "INVALID_PICKUP" ->
                    "Invalid pickup";

            case "INVALID_DATE_RANGE" ->
                    "Invalid date range";

            default ->
                    "Request could not be processed";
        };
    }
}