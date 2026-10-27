package com.flaketax.web;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);
    public static class Fail extends RuntimeException {
        private final HttpStatus status;
        public Fail(HttpStatus status, String message) { super(message); this.status = status; }
        public HttpStatus status() { return status; }
    }
    @ExceptionHandler(Fail.class)
    ResponseEntity<Map<String,String>> fail(Fail e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String,String>> invalid(MethodArgumentNotValidException e) {
        var field = e.getBindingResult().getFieldError();
        String msg = field == null ? "Invalid request" : field.getField() + ": " + field.getDefaultMessage();
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String,String>> badJson(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid JSON or date"));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String,String>> largePhoto(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", "Photo is too large. Maximum size is 2 MB."));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String,String>> conflict(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Conflicting data"));
    }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String,String>> wrongMethod(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Map.of("error", "Wrong request method"));
    }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String,String>> missing(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Page not found"));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String,String>> crash(Exception e) {
        log.error("Unexpected request failure", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Unexpected server error"));
    }
}