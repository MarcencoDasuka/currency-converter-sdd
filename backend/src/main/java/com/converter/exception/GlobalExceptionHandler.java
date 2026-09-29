package com.converter.exception;

import com.converter.dto.ProblemDetailsDto;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

        ProblemDetailsDto problem = new ProblemDetailsDto(
                URI.create("https://api.currency-converter.local/errors/validation-failed"),
                "Validation Failed",
                HttpStatus.BAD_REQUEST.value(),
                "Input parameter validation failed. Please check invalidParams.",
                request.getRequestURI(),
                Map.of(),
                invalidParams
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetailsDto> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        ProblemDetailsDto problem = new ProblemDetailsDto(
                URI.create("https://api.currency-converter.local/errors/bad-request"),
                "Bad Request",
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                request.getRequestURI(),
                Map.of(),
                List.of()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(CurrencyNotFoundException.class)
    public ResponseEntity<ProblemDetailsDto> handleCurrencyNotFound(
            CurrencyNotFoundException ex,
            HttpServletRequest request
    ) {
        ProblemDetailsDto problem = new ProblemDetailsDto(
                URI.create("https://api.currency-converter.local/errors/currency-not-found"),
                "Currency Not Found",
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                request.getRequestURI(),
                Map.of(),
                List.of()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(BulletinUnavailableException.class)
    public ResponseEntity<ProblemDetailsDto> handleBulletinUnavailable(
            BulletinUnavailableException ex,
            HttpServletRequest request
    ) {
        log.error("Exchange rate bulletin unavailable: {}", ex.getMessage());
        ProblemDetailsDto problem = new ProblemDetailsDto(
                URI.create("https://api.currency-converter.local/errors/bulletin-unavailable"),
                "Bulletin Unavailable",
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                ex.getMessage(),
                request.getRequestURI(),
                Map.of(),
                List.of()
        );

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetailsDto> handleGenericException(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("Unhandled internal server error", ex);
        ProblemDetailsDto problem = new ProblemDetailsDto(
                URI.create("https://api.currency-converter.local/errors/internal-error"),
                "Internal Server Error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected internal error occurred. Please try again later.",
                request.getRequestURI(),
                Map.of(),
                List.of()
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
