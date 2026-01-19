package com.martyna.ScenicRoutes.exception;

import java.time.LocalDateTime;

public class ErrorResponse {
    private String message;
    private String details;
    private LocalDateTime timestamp;
    private String errorCode;

    public ErrorResponse(String message, String details, String errorCode) {
        this.message = message;
        this.details = details;
        this.timestamp = LocalDateTime.now();
        this.errorCode = errorCode;
    }

    // Getters
    public String getMessage() {
        return message;
    }

    public String getDetails() {
        return details;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
