package com.team6.backend.category.domain.exception;

import com.team6.backend.global.infrastructure.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CategoryErrorCode implements ErrorCode {

    CATEGORY_IN_USE("CATEGORY_400", HttpStatus.BAD_REQUEST, "이미 사용중인 카테고리입니다."),
    CATEGORY_NOT_FOUND("CATEGORY_404", HttpStatus.NOT_FOUND, "해당 카테고리가 존재하지 않습니다."),
    CATEGORY_FORBIDDEN("CATEGORY_403", HttpStatus.FORBIDDEN, "카테고리에 대한 권한이 없습니다."),
    DUPLICATE_CATEGORY_NAME("CATEGORY_409", HttpStatus.CONFLICT, "이미 존재하는 카테고리 이름입니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;

}
