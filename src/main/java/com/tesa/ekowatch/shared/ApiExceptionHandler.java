package com.tesa.ekowatch.shared;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(RuleViolation.class)
    public ResponseEntity<ApiError> handleRuleViolation(
            RuleViolation exception,
            HttpServletRequest request) {

        String rawCode = exception.code() == null ? "INTERNAL_ERROR" : exception.code();
        String code = canonicalCode(rawCode);
        HttpStatus status = statusFor(rawCode);

        return problemResponse(
                status,
                typeFor(code),
                titleFor(code),
                code,
                detailFor(rawCode),
                request,
                null
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        List<ApiError.FieldError> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> new ApiError.FieldError(
                        err.getField(),
                        err.getDefaultMessage() == null || err.getDefaultMessage().isBlank()
                                ? "Invalid value."
                                : err.getDefaultMessage()
                ))
                .toList();

        return problemResponse(
                HttpStatus.BAD_REQUEST,
                typeFor("VALIDATION_FAILED"),
                titleFor("VALIDATION_FAILED"),
                "VALIDATION_FAILED",
                "Check the highlighted fields.",
                request,
                fieldErrors
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSize(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request) {

        return problemResponse(
                HttpStatus.PAYLOAD_TOO_LARGE,
                typeFor("PHOTO_TOO_LARGE"),
                titleFor("PHOTO_TOO_LARGE"),
                "PHOTO_TOO_LARGE",
                detailFor("PHOTO_TOO_LARGE"),
                request,
                null
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request) {

        String path = request == null ? "" : String.valueOf(request.getRequestURI());
        if (path.contains("/photos")) {
            return problemResponse(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    typeFor("UNSUPPORTED_PHOTO_TYPE"),
                    titleFor("UNSUPPORTED_PHOTO_TYPE"),
                    "UNSUPPORTED_PHOTO_TYPE",
                    detailFor("UNSUPPORTED_PHOTO_TYPE"),
                    request,
                    null
            );
        }

        return problemResponse(
                HttpStatus.BAD_REQUEST,
                typeFor("VALIDATION_FAILED"),
                titleFor("VALIDATION_FAILED"),
                "VALIDATION_FAILED",
                detailFor("VALIDATION_FAILED"),
                request,
                null
        );
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            MissingServletRequestPartException.class,
            MultipartException.class
    })
    public ResponseEntity<ApiError> handleMalformedRequest(
            Exception exception,
            HttpServletRequest request) {

        return problemResponse(
                HttpStatus.BAD_REQUEST,
                typeFor("VALIDATION_FAILED"),
                titleFor("VALIDATION_FAILED"),
                "VALIDATION_FAILED",
                detailFor("VALIDATION_FAILED"),
                request,
                null
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthenticationException(
            AuthenticationException exception,
            HttpServletRequest request) {

        return problemResponse(
                HttpStatus.UNAUTHORIZED,
                typeFor("UNAUTHENTICATED"),
                titleFor("UNAUTHENTICATED"),
                "UNAUTHENTICATED",
                detailFor("UNAUTHENTICATED"),
                request,
                null
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDeniedException(
            AccessDeniedException exception,
            HttpServletRequest request) {

        return problemResponse(
                HttpStatus.FORBIDDEN,
                typeFor("FORBIDDEN"),
                titleFor("FORBIDDEN"),
                "FORBIDDEN",
                detailFor("FORBIDDEN"),
                request,
                null
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {

        return problemResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                typeFor("INTERNAL_ERROR"),
                titleFor("INTERNAL_ERROR"),
                "INTERNAL_ERROR",
                detailFor("INTERNAL_ERROR"),
                request,
                null
        );
    }

    public static ApiError buildProblem(
            HttpStatus status,
            String code,
            String detail,
            HttpServletRequest request,
            List<ApiError.FieldError> fieldErrors) {

        String canonical = canonicalCode(code);
        return new ApiError(
                typeFor(canonical),
                titleFor(canonical),
                status.value(),
                canonical,
                detail == null ? detailFor(canonical) : detail,
                request == null ? null : request.getRequestURI(),
                resolveRequestId(request),
                fieldErrors
        );
    }

    private ResponseEntity<ApiError> problemResponse(
            HttpStatus status,
            String type,
            String title,
            String code,
            String detail,
            HttpServletRequest request,
            List<ApiError.FieldError> fieldErrors) {

        String requestId = resolveRequestId(request);
        ApiError body = new ApiError(
                type,
                title,
                status.value(),
                code,
                detail,
                request == null ? null : request.getRequestURI(),
                requestId,
                fieldErrors
        );

        return ResponseEntity
                .status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header("X-Request-Id", requestId)
                .body(body);
    }

    public static String resolveRequestId(HttpServletRequest request) {
        String mdcId = MDC.get("requestId");
        if (mdcId != null && !mdcId.isBlank()) {
            return mdcId;
        }
        if (request != null) {
            String header = request.getHeader("X-Request-Id");
            if (header != null && header.matches("[A-Za-z0-9._-]{1,64}")) {
                return header;
            }
        }
        return UUID.randomUUID().toString();
    }

    private static String canonicalCode(String code) {
        if (code == null) {
            return "INTERNAL_ERROR";
        }
        return switch (code) {
            case "INVALID_REPORT",
                 "INVALID_LOCATION",
                 "INVALID_PAGE",
                 "INVALID_PICKUP",
                 "INVALID_DATE_RANGE",
                 "VALIDATION_ERROR",
                 "INVALID_REQUEST" -> "VALIDATION_FAILED";
            case "REPORT_NOT_FOUND" -> "RESOURCE_NOT_FOUND";
            case "SELF_CONFIRMATION" -> "SELF_CONFIRMATION_NOT_ALLOWED";
            default -> code;
        };
    }

    private static HttpStatus statusFor(String code) {
        return switch (code) {
            case "VALIDATION_FAILED",
                 "IDEMPOTENCY_KEY_REQUIRED",
                 "INVALID_REPORT",
                 "INVALID_LOCATION",
                 "INVALID_PAGE",
                 "INVALID_PICKUP",
                 "INVALID_DATE_RANGE" -> HttpStatus.BAD_REQUEST;
            case "UNAUTHENTICATED" -> HttpStatus.UNAUTHORIZED;
            case "FORBIDDEN", "WARD_ASSIGNMENT_REQUIRED" -> HttpStatus.FORBIDDEN;
            case "RESOURCE_NOT_FOUND", "REPORT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "SELF_CONFIRMATION_NOT_ALLOWED",
                 "SELF_CONFIRMATION",
                 "IDEMPOTENCY_KEY_REUSED",
                 "DUPLICATE_CONFIRMATION",
                 "DUPLICATE_PICKUP",
                 "REPORT_NOT_ELIGIBLE" -> HttpStatus.CONFLICT;
            case "PHOTO_TOO_LARGE" -> HttpStatus.PAYLOAD_TOO_LARGE;
            case "UNSUPPORTED_PHOTO_TYPE" -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static String typeFor(String code) {
        return switch (code) {
            case "VALIDATION_FAILED" -> "urn:ekowatch:problem:validation";
            case "IDEMPOTENCY_KEY_REQUIRED" -> "urn:ekowatch:problem:idempotency-key-required";
            case "UNAUTHENTICATED" -> "urn:ekowatch:problem:unauthenticated";
            case "FORBIDDEN" -> "urn:ekowatch:problem:forbidden";
            case "WARD_ASSIGNMENT_REQUIRED" -> "urn:ekowatch:problem:ward-assignment-required";
            case "RESOURCE_NOT_FOUND" -> "urn:ekowatch:problem:not-found";
            case "SELF_CONFIRMATION_NOT_ALLOWED" -> "urn:ekowatch:problem:self-confirmation-not-allowed";
            case "IDEMPOTENCY_KEY_REUSED" -> "urn:ekowatch:problem:idempotency-key-reused";
            case "PHOTO_TOO_LARGE" -> "urn:ekowatch:problem:photo-too-large";
            case "UNSUPPORTED_PHOTO_TYPE" -> "urn:ekowatch:problem:unsupported-photo-type";
            case "DUPLICATE_CONFIRMATION", "DUPLICATE_PICKUP", "REPORT_NOT_ELIGIBLE" -> "urn:ekowatch:problem:conflict";
            default -> "urn:ekowatch:problem:internal-error";
        };
    }

    private static String titleFor(String code) {
        return switch (code) {
            case "VALIDATION_FAILED" -> "Validation failed";
            case "IDEMPOTENCY_KEY_REQUIRED" -> "Idempotency key required";
            case "UNAUTHENTICATED" -> "Authentication required";
            case "FORBIDDEN" -> "Access denied";
            case "WARD_ASSIGNMENT_REQUIRED" -> "Ward assignment required";
            case "RESOURCE_NOT_FOUND" -> "Resource not found";
            case "SELF_CONFIRMATION_NOT_ALLOWED" -> "Self confirmation not allowed";
            case "IDEMPOTENCY_KEY_REUSED" -> "Idempotency key reused";
            case "PHOTO_TOO_LARGE" -> "Photo too large";
            case "UNSUPPORTED_PHOTO_TYPE" -> "Unsupported photo type";
            case "DUPLICATE_CONFIRMATION", "DUPLICATE_PICKUP", "REPORT_NOT_ELIGIBLE" -> "Conflict";
            default -> "Internal error";
        };
    }

    private static String detailFor(String code) {
        return switch (code) {
            case "VALIDATION_FAILED",
                 "INVALID_REPORT",
                 "INVALID_LOCATION",
                 "INVALID_PAGE",
                 "INVALID_PICKUP",
                 "INVALID_DATE_RANGE" -> "Check the highlighted fields.";
            case "IDEMPOTENCY_KEY_REQUIRED" -> "Header Idempotency-Key is required for report creation.";
            case "UNAUTHENTICATED" -> "Valid authentication credentials are required.";
            case "FORBIDDEN" -> "You are not authorized to perform this action.";
            case "WARD_ASSIGNMENT_REQUIRED" -> "You are not assigned to this ward.";
            case "RESOURCE_NOT_FOUND", "REPORT_NOT_FOUND" -> "The requested resource was not found.";
            case "SELF_CONFIRMATION_NOT_ALLOWED", "SELF_CONFIRMATION" -> "You cannot confirm your own report.";
            case "IDEMPOTENCY_KEY_REUSED" -> "Idempotency-Key was already used with a different request payload.";
            case "DUPLICATE_CONFIRMATION" -> "You have already confirmed this report.";
            case "DUPLICATE_PICKUP" -> "A collection has already been recorded for this street and date.";
            case "REPORT_NOT_ELIGIBLE" -> "This report cannot be confirmed.";
            case "PHOTO_TOO_LARGE" -> "Uploaded photo exceeds the 5 MiB size limit.";
            case "UNSUPPORTED_PHOTO_TYPE" -> "Only JPEG and PNG photos are supported.";
            default -> "An unexpected error occurred.";
        };
    }
}
