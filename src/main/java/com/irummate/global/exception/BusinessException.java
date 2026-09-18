package com.irummate.global.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException{

    private final ErrorCode errorCode;


    // Error Code에 정의된 Exception을 일으키고 싶을 때
    // throw new BusinessException(Errorcode.Code이름)
    public BusinessException(ErrorCode errorCode){
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    // Error code에 정의된 Exception 설명 외에 다른 설명을 달고 싶을 때
    // throw new BusinessException(Errorcode.Code이름, 설명)
    public BusinessException(ErrorCode errorCode, String customMessage){
        super(customMessage);
        this.errorCode = errorCode;
    }

}
