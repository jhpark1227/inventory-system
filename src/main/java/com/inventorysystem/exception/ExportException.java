package com.inventorysystem.exception;

/** 다운로드 파일 생성 실패. HTTP 500으로 응답한다. */
public class ExportException extends RuntimeException {

    public ExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
