package com.team6.backend.address.presentation.dto.response;

import com.team6.backend.address.domain.entity.Address;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressResponse {

    private UUID adId;
    private String address;
    private String detail;
    private boolean isDefault;
    private String alias;

    public AddressResponse(Address addressEntity) {
        this.adId = addressEntity.getAdId();
        this.address = addressEntity.getAddress();
        this.detail = addressEntity.getDetail();
        this.isDefault = addressEntity.isDefault();
        this.alias = addressEntity.getAlias();
    }

}
