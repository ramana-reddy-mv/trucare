package com.trucare.policy.exception;

public class MemberServiceUnavailableException extends RuntimeException {

    public MemberServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public MemberServiceUnavailableException(String message) {
        super(message);
    }
}
