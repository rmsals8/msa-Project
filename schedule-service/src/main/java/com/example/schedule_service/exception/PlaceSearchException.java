package com.example.schedule_service.exception;

public class PlaceSearchException extends RuntimeException {
    public PlaceSearchException(String message) {
        super(message);
    }
    
    public PlaceSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
