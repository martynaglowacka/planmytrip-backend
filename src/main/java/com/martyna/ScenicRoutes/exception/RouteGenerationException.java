package com.martyna.ScenicRoutes.exception;

public class RouteGenerationException extends RuntimeException {
    private final String errorCode;

    public RouteGenerationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public RouteGenerationException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
