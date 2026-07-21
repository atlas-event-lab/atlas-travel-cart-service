package com.atlas.cart.shared.exception;

import com.atlas.cart.exception.CartExpiredException;
import com.atlas.cart.exception.CartItemNotFoundException;
import com.atlas.cart.exception.CartNotActiveException;
import com.atlas.cart.exception.CartNotFoundException;
import com.atlas.cart.exception.CartNotOwnedException;
import com.atlas.cart.exception.CurrencyMismatchException;
import com.atlas.cart.shared.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldErrorDetail(e.getField(), e.getDefaultMessage()))
                .toList();
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                ProblemTypes.VALIDATION,
                "Validation Error",
                request);
        problem.setProperty("errors", errors);
        return respond(HttpStatus.BAD_REQUEST, problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST, "Malformed request body", ProblemTypes.VALIDATION, "Validation Error", request);
        return respond(HttpStatus.BAD_REQUEST, problem);
    }

    @ExceptionHandler(com.atlas.cart.exception.CartValidationException.class)
    public ResponseEntity<ProblemDetail> handleCartValidation(
            com.atlas.cart.exception.CartValidationException ex, HttpServletRequest request) {
        ProblemDetail problem =
                problem(HttpStatus.BAD_REQUEST, ex.getMessage(), ProblemTypes.VALIDATION, "Validation Error", request);
        return respond(HttpStatus.BAD_REQUEST, problem);
    }

    @ExceptionHandler({CartNotFoundException.class, CartItemNotFoundException.class})
    public ResponseEntity<ProblemDetail> handleNotFound(RuntimeException ex, HttpServletRequest request) {
        return respond(
                HttpStatus.NOT_FOUND,
                problem(HttpStatus.NOT_FOUND, ex.getMessage(), ProblemTypes.NOT_FOUND, "Not Found", request));
    }

    @ExceptionHandler(CartNotOwnedException.class)
    public ResponseEntity<ProblemDetail> handleForbidden(CartNotOwnedException ex, HttpServletRequest request) {
        return respond(
                HttpStatus.FORBIDDEN,
                problem(HttpStatus.FORBIDDEN, ex.getMessage(), ProblemTypes.FORBIDDEN, "Forbidden", request));
    }

    @ExceptionHandler(CartExpiredException.class)
    public ResponseEntity<ProblemDetail> handleGone(CartExpiredException ex, HttpServletRequest request) {
        return respond(HttpStatus.GONE, problem(HttpStatus.GONE, ex.getMessage(), ProblemTypes.GONE, "Gone", request));
    }

    @ExceptionHandler(CartNotActiveException.class)
    public ResponseEntity<ProblemDetail> handleConflict(CartNotActiveException ex, HttpServletRequest request) {
        return respond(
                HttpStatus.CONFLICT,
                problem(HttpStatus.CONFLICT, ex.getMessage(), ProblemTypes.CONFLICT, "Conflict", request));
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    public ResponseEntity<ProblemDetail> handleUnprocessable(CurrencyMismatchException ex, HttpServletRequest request) {
        return respond(
                HttpStatus.UNPROCESSABLE_ENTITY,
                problem(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ex.getMessage(),
                        ProblemTypes.UNPROCESSABLE,
                        "Unprocessable Entity",
                        request));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return respond(
                HttpStatus.NOT_FOUND,
                problem(HttpStatus.NOT_FOUND, "Resource not found", ProblemTypes.NOT_FOUND, "Not Found", request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex, HttpServletRequest request) {
        LOGGER.error("Unexpected error processing request to {}", request.getRequestURI(), ex);
        return respond(
                HttpStatus.INTERNAL_SERVER_ERROR,
                problem(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "An unexpected error occurred",
                        ProblemTypes.INTERNAL_ERROR,
                        "Internal Server Error",
                        request));
    }

    private ProblemDetail problem(
            HttpStatus status, String detail, URI type, String title, HttpServletRequest request) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setType(type);
        p.setTitle(title);
        p.setInstance(URI.create(request.getRequestURI()));
        p.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        return p;
    }

    private ResponseEntity<ProblemDetail> respond(HttpStatus status, ProblemDetail problem) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
