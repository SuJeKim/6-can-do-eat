package com.team6.backend.address.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AddressUpdateRequest {

    @NotBlank(message = "주소(address)는 필수 입력사항입니다")
    private String address;

    private String detail;

    private String alias;

}
