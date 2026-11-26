package com.converter.common.web;

import com.converter.common.exception.ApiException;
import com.converter.common.exception.PlatformExceptions.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class BaseProblemDetailsHandler {

    private static final Logger log = LoggerFactory.getLogger(BaseProblemDetailsHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetailsDto> handleApiException(ApiException ex, HttpServletRequest request) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("Server API error ({}): {}", ex.getStatus().value(), ex.getMessage());
        }
        Map<String, Object> props = baseProperties(request);
        ResponseEntity.BodyBuilder builder = ResponseEntity
                .status(ex.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (ex instanceof TooManyRequestsException rateEx) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(rateEx.getRetryAfterSeconds()));
            props.put("retryAfterSeconds", rateEx.getRetryAfterSeconds());
        }
        return builder.body(new ProblemDetailsDto(
                ex.getType(),
                ex.getTitle(),
                ex.getStatus().value(),
                ex.getMessage(),
                request.getRequestURI(),
                props,
                List.of()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetailsDto> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<ProblemDetailsDto.InvalidParam> invalidParams = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            invalidParams.add(new ProblemDetailsDto.InvalidParam(
                    fieldError.getField(),
                    fieldError.getDefaultMessage()
            ));
        }
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(new ProblemDetailsDto(
                        URI.create("https://api.currency-converter.local/errors/validation-failed"),
                        "Validation Failed",
                        HttpStatus.BAD_REQUEST.value(),
                        "Input parameter validation failed. Please check invalidParams.",
                        request.getRequestURI(),
                        baseProperties(request),
                        invalidParams
                ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetailsDto> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        List<ProblemDetailsDto.InvalidParam> invalidParams = new ArrayList<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            invalidParams.add(new ProblemDetailsDto.InvalidParam(
                    violation.getPropertyPath().toString(),
                    violation.getMessage()
            ));
        }
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(new ProblemDetailsDto(
                        URI.create("https://api.currency-converter.local/errors/validation-failed"),
                        "Validation Failed",
                        HttpStatus.BAD_REQUEST.value(),
                        "Constraint validation failed.",
                        request.getRequestURI(),
                        baseProperties(request),
                        invalidParams
                ));
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ProblemDetailsDto> handleBadRequest(Exception ex, HttpServletRequest request) {
        String detail = ex instanceof IllegalArgumentException
                ? ex.getMessage()
                : "Malformed request payload or parameter";
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(new ProblemDetailsDto(
                        URI.create("https://api.currency-converter.local/errors/bad-request"),
                        "Bad Request",
                        HttpStatus.BAD_REQUEST.value(),
                        detail,
                        request.getRequestURI(),
                        baseProperties(request),
                        List.of()
                ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetailsDto> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(new ProblemDetailsDto(
                        URI.create("https://api.currency-converter.local/errors/not-found"),
                        "Not Found",
                        HttpStatus.NOT_FOUND.value(),
                        "The requested endpoint does not exist.",
                        request.getRequestURI(),
                        baseProperties(request),
                        List.of()
                ));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetailsDto> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(new ProblemDetailsDto(
                        URI.create("https://api.currency-converter.local/errors/method-not-allowed"),
                        "Method Not Allowed",
                        HttpStatus.METHOD_NOT_ALLOWED.value(),
                        "HTTP method is not supported for this endpoint.",
                        request.getRequestURI(),
                        baseProperties(request),
                        List.of()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetailsDto> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled internal server error on {}", request.getRequestURI(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(new ProblemDetailsDto(
                        URI.create("https://api.currency-converter.local/errors/internal-error"),
                        "Internal Server Error",
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "An unexpected internal error occurred. Please try again later.",
                        request.getRequestURI(),
                        baseProperties(request),
                        List.of()
                ));
    }

    private Map<String, Object> baseProperties(HttpServletRequest request) {
        Map<String, Object> map = new LinkedHashMap<>();
        String requestId = RequestIdFilter.currentRequestId(request);
        if (requestId != null && !requestId.isBlank()) {
            map.put("requestId", requestId);
        }
        return map;
    }
}
