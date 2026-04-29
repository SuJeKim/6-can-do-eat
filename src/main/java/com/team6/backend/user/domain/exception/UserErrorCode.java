package com.team6.backend.user.domain.exception;

import com.team6.backend.global.infrastructure.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    DUPLICATE_USERNAME("USER_409", HttpStatus.CONFLICT, "이미 사용 중인 이름입니다."),
    PASSWORD_ALREADY_IN_USE("USER_409", HttpStatus.CONFLICT, "현재 비밀번호와 동일합니다. 다른 비밀번호를 사용해주세요.");

    private final String code;
    private final HttpStatus status;
    private final String message;
}
