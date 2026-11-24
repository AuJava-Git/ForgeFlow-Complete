package com.forgeflow.platform.error;

import org.springframework.http.HttpStatus;

public class BusinessRuleException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public BusinessRuleException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }

    public static BusinessRuleException badRequest(String code, String message) {
        return new BusinessRuleException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static BusinessRuleException notFound(String code, String message) {
        return new BusinessRuleException(HttpStatus.NOT_FOUND, code, message);
    }

    public static BusinessRuleException conflict(String code, String message) {
        return new BusinessRuleException(HttpStatus.CONFLICT, code, message);
    }
}
