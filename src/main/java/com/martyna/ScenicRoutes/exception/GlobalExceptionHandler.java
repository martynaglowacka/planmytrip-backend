package com.martyna.ScenicRoutes.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RouteGenerationException.class)
    public ResponseEntity<ErrorResponse> handleRouteGenerationException(RouteGenerationException ex) {
        ErrorResponse error = new ErrorResponse(
                ex.getMessage(),
                "Unable to generate route with the given parameters",
                ex.getErrorCode()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ErrorResponse> handleGoogleApiClientError(HttpClientErrorException ex) {
        ErrorResponse error = new ErrorResponse(
                "Google Maps API error",
                "Invalid API request. Please check your location coordinates.",
                "GOOGLE_API_CLIENT_ERROR"
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<ErrorResponse> handleGoogleApiServerError(HttpServerErrorException ex) {
        ErrorResponse error = new ErrorResponse(
                "Google Maps service temporarily unavailable",
                "Please try again in a few moments",
                "GOOGLE_API_SERVER_ERROR"
        );
        return new ResponseEntity<>(error, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ErrorResponse> handleNetworkError(ResourceAccessException ex) {
        ErrorResponse error = new ErrorResponse(
                "Network error",
                "Unable to connect to Google Maps service. Check your internet connection.",
                "NETWORK_ERROR"
        );
        return new ResponseEntity<>(error, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        ErrorResponse error = new ErrorResponse(
                "Invalid parameters",
                ex.getMessage(),
                "INVALID_PARAMETERS"
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    // Handle IllegalStateException for preference/scheduling errors
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        ErrorResponse error = new ErrorResponse(
                ex.getMessage(),
                "Unable to create route with current settings",
                "SCHEDULING_ERROR"
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        // Log the actual exception for debugging
        ex.printStackTrace();

        ErrorResponse error = new ErrorResponse(
                "An unexpected error occurred",
                "Please try again or contact support if the problem persists",
                "INTERNAL_ERROR"
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
