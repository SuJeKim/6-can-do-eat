package com.team6.backend.address.domain.entity;

import com.team6.backend.address.presentation.dto.request.AddressRequest;
import com.team6.backend.address.presentation.dto.request.AddressUpdateRequest;
import com.team6.backend.global.infrastructure.entity.BaseEntity;
import com.team6.backend.user.domain.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "p_address")
public class Address extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "address_id")
    private UUID adId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id",nullable = false)
    private User user;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "detail")
    private String detail;

    // 검색.....
    @Column(name = "alias")
    private String alias;

    // 기본 배송지 여부
    @Column(name = "is_default")
    private boolean isDefault;

    public Address(AddressRequest request, User user) {
        this.address = request.getAddress();
        this.detail = request.getDetail();
        this.isDefault = request.isDefault();
        this.user = user;
        this.alias = request.getAlias();
    }

    public void updateAddress(AddressUpdateRequest request) {
        this.address = request.getAddress();
        this.detail = request.getDetail();
        this.alias = request.getAlias();
    }

    public void updateDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

}
