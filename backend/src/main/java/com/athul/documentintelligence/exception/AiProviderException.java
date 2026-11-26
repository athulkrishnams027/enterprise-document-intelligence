package com.athul.documentintelligence.exception;

import org.springframework.http.HttpStatus;

public class AiProviderException extends DomainException {
    public AiProviderException(String errorCode, String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, errorCode, message, cause);
    }

    public AiProviderException(String errorCode, String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, errorCode, message);
    }
}
