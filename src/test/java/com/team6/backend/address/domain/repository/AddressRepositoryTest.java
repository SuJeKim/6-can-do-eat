package com.team6.backend.address.domain.repository;

import com.team6.backend.address.domain.entity.Address;
import com.team6.backend.address.presentation.dto.request.AddressRequest;
import com.team6.backend.global.infrastructure.config.AuditorConfig;
import com.team6.backend.global.infrastructure.config.JpaAuditingConfig;
import com.team6.backend.user.domain.entity.Role;
import com.team6.backend.user.domain.entity.User;
import com.team6.backend.user.domain.repository.UserInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({JpaAuditingConfig.class, AuditorConfig.class})
class AddressRepositoryTest {

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private UserInfoRepository userInfoRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .username("testuser")
                .password("password")
                .role(Role.CUSTOMER)
                .nickname("tester")
                .build();
        userInfoRepository.save(user);
    }

    @Test
    @DisplayName("사용자 ID로 주소 목록 조회")
    void findByUserId() {
        // given
        AddressRequest request1 = new AddressRequest("서울시", "강남구", false, "집");
        AddressRequest request2 = new AddressRequest("경기도", "성남시", false, "회사");
        addressRepository.save(new Address(request1, user));
        addressRepository.save(new Address(request2, user));

        Pageable pageable = PageRequest.of(0, 10);

        // when
        Page<Address> result = addressRepository.findByUserId(user.getId(), pageable);

        // then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("사용자 ID와 별칭으로 주소 목록 검색 (대소문자 무시)")
    void findByUserIdAndAliasContainingIgnoreCase() {
        // given
        AddressRequest request1 = new AddressRequest("서울시", "강남구", false, "HOME");
        AddressRequest request2 = new AddressRequest("경기도", "성남시", false, "Office");
        addressRepository.save(new Address(request1, user));
        addressRepository.save(new Address(request2, user));

        Pageable pageable = PageRequest.of(0, 10);

        // when
        Page<Address> result = addressRepository.findByUserIdAndAliasContainingIgnoreCase(user.getId(), "home", pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getAlias()).isEqualTo("HOME");
    }

    @Test
    @DisplayName("사용자 ID로 기본 배송지 조회")
    void findByUserIdAndIsDefaultTrue() {
        // given
        AddressRequest request1 = new AddressRequest("서울시", "강남구", true, "기본");
        AddressRequest request2 = new AddressRequest("경기도", "성남시", false, "일반");
        addressRepository.save(new Address(request1, user));
        addressRepository.save(new Address(request2, user));

        // when
        Optional<Address> result = addressRepository.findByUserIdAndIsDefaultTrue(user.getId());

        // then
        assertThat(result).isPresent();
        assertThat(result.get().isDefault()).isTrue();
        assertThat(result.get().getAlias()).isEqualTo("기본");
    }
}
