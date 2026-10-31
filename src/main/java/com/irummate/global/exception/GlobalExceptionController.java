package com.irummate.global.exception;


import com.irummate.global.response.GlobalApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionController {

    // 요청 관리 Handler
    @ExceptionHandler({
            CannotCreateTransactionException.class,
            DataAccessResourceFailureException.class
    })
    public ResponseEntity<GlobalApiResponse<?>> handleDatabaseBusy(
            Exception e
    ) {
        log.warn("Database unavailable or pool exhausted: {}", e.getMessage());

        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;

        return ResponseEntity
                .status(status)
                .header(HttpHeaders.RETRY_AFTER, "2")
                .body(GlobalApiResponse.error(
                        status,
                        "요청이 몰리고 있습니다. 잠시 후 다시 시도해주세요.",
                        null
                ));
    }


    // Handler 처리되지 않은 다른 모든 예외들이 발생시키는 예외 처리용
    // 500 Internal Server Error
    @ExceptionHandler(Exception.class)
    public ResponseEntity<GlobalApiResponse<?>> handleException(Exception e){


        log.warn("Internal Server Error: {}",e.getMessage());

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        return ResponseEntity
                .status(status)
                .body(GlobalApiResponse.error(status, "서버 내부 오류입니다.", null));
    }


    // Jakarta Validation(@NonNull, @NotEmpty, @NotBlank 등)
    // 컨트롤러가 JSON을 DTO 객체에 매핑할 때 값이 null이면 MethodArgumentNotValidException 발생
    // 그에 해당하는 Handler로 어떤 부분이 null인지도 확인
    // 400 Bad Request
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GlobalApiResponse<?>> handleValidationException(MethodArgumentNotValidException e){

        log.warn("Validation failed: {}", e.getMessage());

        List<GlobalApiResponse.ErrorResponse> errors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new GlobalApiResponse.ErrorResponse(
                        fe.getField(),           // "bio"
                        fe.getDefaultMessage()   // "100자를 초과할 수 없습니다."
                ))
                .toList();

        HttpStatus status = HttpStatus.BAD_REQUEST;

        return ResponseEntity
                .status(status)
                .body(GlobalApiResponse.error(status, "필수 항목 누락 / 값 범위 초과", errors));
    }


    // Business 영역에서 발생하는 예외 Handler 처리
    // ErrorCode에 정의된 Code들로 BusinessException을 발생시키면 동작
    // 정의된 각 Http Status 대로 처리
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<GlobalApiResponse<?>> handleBusinessException(BusinessException e){

        log.warn("BusinessException: {}", e.getMessage());

        ErrorCode errorCode = e.getErrorCode();

        HttpStatus httpStatus = errorCode.getHttpStatus();

        return ResponseEntity
                .status(httpStatus)
                .body(GlobalApiResponse.error(httpStatus, e.getMessage(), null));

    }

}
