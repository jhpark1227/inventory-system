package com.inventorysystem.exception;

/** 사용자 요청 오류. HTTP 400으로 응답하며, 메시지는 사용자에게 그대로 보여준다. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
