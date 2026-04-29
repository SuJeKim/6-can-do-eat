package com.team6.backend.address.application.service;

import com.team6.backend.address.domain.entity.Address;
import com.team6.backend.address.domain.repository.AddressRepository;
import com.team6.backend.address.presentation.dto.request.AddressRequest;
import com.team6.backend.address.presentation.dto.request.AddressUpdateRequest;
import com.team6.backend.address.presentation.dto.response.AddressResponse;
import com.team6.backend.global.infrastructure.config.security.util.SecurityUtils;
import com.team6.backend.user.domain.entity.Role;
import com.team6.backend.user.domain.entity.User;
import com.team6.backend.user.domain.repository.UserInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock
    private AddressRepository addressRepository;
    @Mock
    private UserInfoRepository userInfoRepository;
    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private AddressService addressService;

    private User user;
    private Address address;
    private UUID userId;
    private UUID adId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        adId = UUID.randomUUID();
        
        user = User.builder()
                .id(userId)
                .username("testUser")
                .role(Role.CUSTOMER)
                .build();

        AddressRequest request = new AddressRequest("서울시 강남구", "101호", false, "집");
        address = new Address(request, user);
    }

    @Test
    @DisplayName("배송지 등록 성공")
    void addAddress_Success() {
        // given
        AddressRequest request = new AddressRequest("서울시 강남구", "101호", false, "집");
        given(securityUtils.getCurrentUserRole()).willReturn(Role.CUSTOMER);
        given(securityUtils.getCurrentUserId()).willReturn(userId);
        given(userInfoRepository.getReferenceById(userId)).willReturn(user);
        given(addressRepository.save(any(Address.class))).willReturn(address);

        // when
        AddressResponse response = addressService.addAddress(request);

        // then
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("배송지 목록 조회 성공")
    void getAddress_Success() {
        // given
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Address> addressPage = new PageImpl<>(Collections.singletonList(address));
        given(securityUtils.getCurrentUserRole()).willReturn(Role.CUSTOMER);
        given(securityUtils.getCurrentUserId()).willReturn(userId);
        given(addressRepository.findByUserId(userId, pageable)).willReturn(addressPage);

        // when
        Page<AddressResponse> result = addressService.getAddress(userId, null, 0, 10, "createdAt", false);

        // then
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("배송지 상세 조회 성공")
    void getAddressById_Success() {
        // given
        given(securityUtils.getCurrentUserRole()).willReturn(Role.CUSTOMER);
        given(addressRepository.findById(adId)).willReturn(Optional.of(address));
        given(securityUtils.getCurrentUserId()).willReturn(userId);

        // when
        AddressResponse response = addressService.getAddressById(adId);

        // then
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("배송지 삭제 성공")
    void deleteAddress_Success() {
        // given
        given(securityUtils.getCurrentUserRole()).willReturn(Role.CUSTOMER);
        given(addressRepository.findById(adId)).willReturn(Optional.of(address));
        given(securityUtils.getCurrentUserId()).willReturn(userId);

        // when
        addressService.deleteAddress(adId);

        // then
        assertThat(address.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("배송지 수정 성공 - 기본 배송지 여부는 변경되지 않음")
    void updateAddress_Success() {
        // given
        AddressUpdateRequest request = new AddressUpdateRequest("경기도 성남시", "202호", "회사");
        given(securityUtils.getCurrentUserRole()).willReturn(Role.CUSTOMER);
        given(addressRepository.findById(adId)).willReturn(Optional.of(address));
        given(securityUtils.getCurrentUserId()).willReturn(userId);

        // when
        AddressResponse response = addressService.updateAddress(adId, request);

        // then
        assertThat(response.getAddress()).isEqualTo("경기도 성남시");
        assertThat(response.getDetail()).isEqualTo("202호");
        assertThat(response.getAlias()).isEqualTo("회사");
        assertThat(response.isDefault()).isFalse(); // 초기값 유지
    }

    @Test
    @DisplayName("기본 배송지 설정 성공")
    void updateDefault_Success() {
        // given
        given(securityUtils.getCurrentUserRole()).willReturn(Role.CUSTOMER);
        given(securityUtils.getCurrentUserId()).willReturn(userId);
        given(addressRepository.findById(adId)).willReturn(Optional.of(address));
        given(addressRepository.findByUserIdAndIsDefaultTrue(userId)).willReturn(Optional.empty());

        // when
        AddressResponse response = addressService.updateDefault(adId, true); // Changed from UpdateDefault to updateDefault

        // then
        assertThat(response.isDefault()).isTrue();
    }
}
