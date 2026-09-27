package com.agrioptima.exception;

/** Request is syntactically valid but violates a business rule (e.g. growth stage from another crop). */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
