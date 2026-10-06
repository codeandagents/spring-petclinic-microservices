package org.springframework.samples.petclinic.customers.web;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Returns bean validation failures as 400 with a stable body:
 * {status, error, errors:[{field, defaultMessage}]}.
 */
@RestControllerAdvice
class RestExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        return body(ex.getBindingResult().getFieldErrors().stream()
            .map(e -> Map.of("field", e.getField(), "defaultMessage", String.valueOf(e.getDefaultMessage())))
            .toList());
    }

    /** Raised instead when the controller method also has parameter constraints (e.g. @Min on a path variable). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<Map<String, Object>> handleMethodValidation(HandlerMethodValidationException ex) {
        List<Map<String, String>> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error -> {
                String field = error instanceof FieldError fe ? fe.getField() : String.valueOf(name);
                errors.add(Map.of("field", field, "defaultMessage", String.valueOf(error.getDefaultMessage())));
            });
        });
        return body(errors);
    }

    private ResponseEntity<Map<String, Object>> body(List<Map<String, String>> errors) {
        List<Map<String, String>> sorted = errors.stream()
            .sorted(Comparator.comparing(e -> e.get("field")))
            .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", HttpStatus.BAD_REQUEST.getReasonPhrase());
        body.put("errors", sorted);
        return ResponseEntity.badRequest().body(body);
    }
}
