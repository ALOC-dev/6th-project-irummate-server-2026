package com.irummate.global.exception;


import com.irummate.global.response.GlobalApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
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
    // JSON 형식 오류를 공통 400 응답으로 처리
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<GlobalApiResponse<?>> handleInputFormat(HttpMessageNotReadableException e) {
        log.warn("Invalid request body: {}", e.getClass().getSimpleName());
        return errorResponse(ErrorCode.INVALID_INPUT, "요청 값이나 형식이 올바르지 않습니다.", null);
    }

    // UUID·숫자·Enum 등 요청 파라미터의 타입 변환 실패를 400으로 처리
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<GlobalApiResponse<?>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {

        log.warn("Request parameter type mismatch: {}", e.getName());

        return errorResponse(ErrorCode.INVALID_INPUT, "요청 값이나 형식이 올바르지 않습니다.", null);
    }

    @ExceptionHandler({
            CannotCreateTransactionException.class,
            DataAccessResourceFailureException.class
    })
    public ResponseEntity<GlobalApiResponse<?>> handleDatabaseBusy(
            Exception e
    ) {
        log.warn("Database unavailable or pool exhausted: {}", e.getMessage());

        ErrorCode errorCode = ErrorCode.DATABASE_BUSY;

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .header(HttpHeaders.RETRY_AFTER, "2")
                .body(GlobalApiResponse.error(errorCode, errorCode.getMessage(), null));
    }


    // Handler 처리되지 않은 다른 모든 예외들이 발생시키는 예외 처리용
    // 500 Internal Server Error
    @ExceptionHandler(Exception.class)
    public ResponseEntity<GlobalApiResponse<?>> handleException(Exception e){


        log.warn("Internal Server Error: {}",e.getMessage());

        return errorResponse(ErrorCode.INTERNAL_ERROR, "서버 내부 오류입니다.", null);
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

        return errorResponse(ErrorCode.INVALID_INPUT, "필수 항목 누락 / 값 범위 초과", errors);
    }


    // Business 영역에서 발생하는 예외 Handler 처리
    // ErrorCode에 정의된 Code들로 BusinessException을 발생시키면 동작
    // 정의된 각 Http Status 대로 처리
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<GlobalApiResponse<?>> handleBusinessException(BusinessException e){

        log.warn("BusinessException: {}", e.getMessage());

        return errorResponse(e.getErrorCode(), e.getMessage(), null);

    }



    // 모든 API에서 발생한 낙관적 잠금 충돌을 409로 응답
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<GlobalApiResponse<?>> handleUpdateConflict(OptimisticLockingFailureException e) {

        log.warn("낙관적 잠금 충돌: exception={}", e.getClass().getSimpleName());

        ErrorCode errorCode = ErrorCode.UPDATE_CONFLICT;

        return errorResponse(errorCode, errorCode.getMessage(), null);
    }

    // 모든 API에서 발생한 비관적 잠금 실패를 409로 응답
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<GlobalApiResponse<?>> handleLockConflict(
            PessimisticLockingFailureException e
    ){

        log.warn("비관적 잠금 획득 실패: exception={}", e.getClass().getSimpleName());

        ErrorCode errorCode = ErrorCode.LOCK_CONFLICT;

        return errorResponse(errorCode, errorCode.getMessage(), null);

    }

    private ResponseEntity<GlobalApiResponse<?>> errorResponse(
            ErrorCode errorCode, String message, List<GlobalApiResponse.ErrorResponse> errors
    ) {
        return ResponseEntity.status(errorCode.getHttpStatus()).body(GlobalApiResponse.error(errorCode, message, errors));
    }
}
