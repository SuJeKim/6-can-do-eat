package com.team6.backend.address.presentation.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AddressRequest {

    @NotBlank(message = "주소(address)는 필수 입력사항입니다")
    private String address;

    private String detail;

    // JSON 필드명 불일치
    // 변수명은 isDefault로 유지하되, JSON의 "default" 키를 매핑
    @JsonProperty("default")
    private boolean isDefault;

    private String alias;

}
