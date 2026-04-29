package com.team6.backend.address.application.service;

import com.team6.backend.address.domain.entity.Address;
import com.team6.backend.address.domain.repository.AddressRepository;
import com.team6.backend.address.presentation.dto.request.AddressRequest;
import com.team6.backend.address.presentation.dto.response.AddressResponse;
import com.team6.backend.address.presentation.dto.request.AddressUpdateRequest;
import com.team6.backend.global.infrastructure.config.security.util.SecurityUtils;
import com.team6.backend.global.infrastructure.exception.ApplicationException;
import com.team6.backend.global.infrastructure.exception.CommonErrorCode;
import com.team6.backend.user.domain.entity.Role;
import com.team6.backend.user.domain.entity.User;
import com.team6.backend.user.domain.repository.UserInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserInfoRepository userInfoRepository;
    private final SecurityUtils securityUtils;

    // 배송지 등록: CUSTOMER 전용
    @Transactional
    public AddressResponse addAddress(AddressRequest request)  {
        validateRole(Role.CUSTOMER);
        UUID userId = securityUtils.getCurrentUserId();
        User user = userInfoRepository.getReferenceById(userId); // DB 조회 없이 프록시 객체 생성
        Address address = addressRepository.save(new Address(request, user));
        return new AddressResponse(address);
    }

    // 내 배송지 목록 조회: CUSTOMER 전용
    @Transactional(readOnly = true)
    public Page<AddressResponse> getAddress(UUID userId, String alias, int page, int size, String sortByTime, boolean isAsc) {
        validateRole(Role.CUSTOMER);
        
        // 본인 확인 (경로의 userId와 로그인한 유저 ID 비교)
        UUID currentUserId = securityUtils.getCurrentUserId();
        if (!currentUserId.equals(userId)) {
            throw new ApplicationException(CommonErrorCode.FORBIDDEN);
        }
        
        Sort.Direction sortDirection = isAsc ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(sortDirection, sortByTime);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Address> addressList;
        if(alias != null && !alias.isEmpty()){
            addressList = addressRepository.findByUserIdAndAliasContainingIgnoreCase(userId, alias, pageable);
        } else {
            addressList = addressRepository.findByUserId(userId, pageable);
        }
        return addressList.map(AddressResponse::new);
    }

    // 배송지 상세 조회: CUSTOMER 전용
    @Transactional(readOnly = true)
    public AddressResponse getAddressById(UUID adId) {
        validateRole(Role.CUSTOMER);
        Address address = findById(adId);
        return new AddressResponse(address);
    }

    // 배송지 삭제: CUSTOMER(본인) 또는 MASTER 가능
    @Transactional
    public void deleteAddress(UUID adId) {
        Role currentRole = securityUtils.getCurrentUserRole();
        if (!currentRole.equals(Role.CUSTOMER) && !currentRole.equals(Role.MASTER)) {
            throw new ApplicationException(CommonErrorCode.FORBIDDEN);
        }
        
        Address address = findById(adId);
        address.markDeleted(securityUtils.getCurrentUserId().toString());
    }

    // 배송지 수정: CUSTOMER 전용
    @Transactional
    public AddressResponse updateAddress(UUID adId, AddressUpdateRequest request) {
        validateRole(Role.CUSTOMER);
        Address address = findById(adId);
        address.updateAddress(request);
        return new AddressResponse(address);
    }

    // 기본 배송지 설정: CUSTOMER 전용
    @Transactional
    public AddressResponse updateDefault(UUID adId, boolean isDefault) {
        validateRole(Role.CUSTOMER);
        UUID currentUserId = securityUtils.getCurrentUserId();
        Address addressToUpdate = findById(adId);

        // 같을 경우 못 바꾸게
        if (isDefault) {
            Optional<Address> currentDefaultAddress = addressRepository.findByUserIdAndIsDefaultTrue(currentUserId);
            currentDefaultAddress.ifPresent(addr -> {
                if (!addr.getAdId().equals(adId)) { // Ensure it's not the same address
                    addr.updateDefault(false);
                }
            });
        }
        
        addressToUpdate.updateDefault(isDefault);
        return new AddressResponse(addressToUpdate);
    }

    // 공통 역할 검증 메서드
    private void validateRole(Role requiredRole) {
        if (!securityUtils.getCurrentUserRole().equals(requiredRole)) {
            throw new ApplicationException(CommonErrorCode.FORBIDDEN);
        }
    }

    // 공통 조회 및 소유권 검증 로직
    private Address findById(UUID adId) {
        Address address = addressRepository.findById(adId)
                .orElseThrow(() -> new ApplicationException(CommonErrorCode.RESOURCE_NOT_FOUND));

        if (address.isDeleted()) {
            throw new ApplicationException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }

        UUID currentUserId = securityUtils.getCurrentUserId();
        Role currentUserRole = securityUtils.getCurrentUserRole();

        // MASTER가 아니고 본인도 아닌 경우 권한 에러
        if (!currentUserRole.equals(Role.MASTER) && !address.getUser().getId().equals(currentUserId)) {
            throw new ApplicationException(CommonErrorCode.FORBIDDEN);
        }

        return address;
    }
}
