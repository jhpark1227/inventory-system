package com.inventorysystem.web;

import com.inventorysystem.dto.ErrorResponse;
import com.inventorysystem.exception.InvalidRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 모든 오류를 {@code {"message": "..."}} 형태로 응답한다.
 * 상태 코드는 사용자 요청 오류(400)와 서버 오류(500) 두 가지만 쓴다.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final String BAD_REQUEST = "잘못된 요청입니다. 입력 내용을 확인해 주세요.";
    private static final String SERVER_ERROR = "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException e) {
        return badRequest(e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException e) {
        return badRequest("파일 크기는 5MB 이하여야 합니다.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOthers(Exception e) {
        // 잘못된 JSON, 필수 파트 누락, 지원하지 않는 메서드 등 스프링이 4xx로 분류한 오류는 모두 400으로 응답한다.
        if (e instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatusCode status = springError.getStatusCode();
            if (status.is4xxClientError()) {
                return badRequest(BAD_REQUEST);
            }
        } else if (e instanceof org.springframework.http.converter.HttpMessageNotReadableException
                || e instanceof org.springframework.web.multipart.MultipartException) {
            return badRequest(BAD_REQUEST);
        }
        log.error("처리 중 오류가 발생했습니다.", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(SERVER_ERROR));
    }

    private ResponseEntity<ErrorResponse> badRequest(String message) {
        return ResponseEntity.badRequest().body(new ErrorResponse(message));
    }
}
