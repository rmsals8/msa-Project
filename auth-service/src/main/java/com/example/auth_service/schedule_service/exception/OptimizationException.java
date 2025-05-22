package com.example.auth_service.schedule_service.exception;

public class OptimizationException extends RuntimeException {
    public OptimizationException(String message) {
        super(message);
    }

    public OptimizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
