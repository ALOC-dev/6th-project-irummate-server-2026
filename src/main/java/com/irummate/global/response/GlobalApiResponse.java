package com.irummate.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GlobalApiResponse<T> {

    private final int status;
    private final String code;
    private final String message;
    private final T data;
    private final List<ErrorResponse> errors;
    private final Integer size;

    public static <T> GlobalApiResponse<T> success(HttpStatus status, String message, T data){
        return GlobalApiResponse.<T>builder()
                .status(status.value())
                .code(status.name())
                .message(message)
                .data(data)
                .build();
    }

    public static <T> GlobalApiResponse<T> multiSuccess(HttpStatus status, String message, T data, int size){
        return GlobalApiResponse.<T>builder()
                .status(status.value())
                .code(status.name())
                .message(message)
                .data(data)
                .size(size)
                .build();
    }

    public static GlobalApiResponse<Void> error(HttpStatus status, String message, List<ErrorResponse> errors){
        return GlobalApiResponse.<Void>builder()
                .status(status.value())
                .code(status.name())
                .message(message)
                .errors((errors != null) ? errors : List.of())
                .build();
    }

    @Getter
    @AllArgsConstructor
    public static class ErrorResponse{
        private final String field;
        private final String reason;
    }
}
