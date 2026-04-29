//package com.team6.backend.address.application.service;
//
//import com.team6.backend.address.domain.entity.Address;
//import com.team6.backend.address.domain.repository.AddressRepository;
//import com.team6.backend.address.presentation.dto.request.AddressRequest;
//import com.team6.backend.address.presentation.dto.response.AddressResponse;
//import com.team6.backend.user.domain.entity.Role;
//import com.team6.backend.user.domain.entity.User;
//import com.team6.backend.user.domain.repository.userInfoRepository;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
//import org.springframework.security.core.authority.SimpleGrantedAuthority;
//import org.springframework.security.core.context.SecurityContextHolder;
//import org.springframework.test.annotation.Rollback;
//import org.springframework.test.context.TestPropertySource;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.List;
//import java.util.UUID;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//@SpringBootTest
//@Transactional
//@Rollback(false)
////@TestPropertySource(properties = {
//// .env 설정들 직접 주입 시켜야 함
//// 그렇지 않을 경우 모든 테스트 실패로 뜸.
////})
//class AddressServiceIntegrationTest {
//
//    @Autowired
//    private AddressService addressService;
//    @Autowired
//    private AddressRepository addressRepository;
//    @Autowired
//    private userInfoRepository userRepository;
//
//    private User testUser;
//
//    @BeforeEach
//    void setUp() {
//        testUser = User.builder()
//                .username("testuser_" + UUID.randomUUID().toString().substring(0,8))
//                .password("password")
//                .role(Role.CUSTOMER)
//                .build();
//        testUser = userRepository.save(testUser);
//
//        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
//        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(testUser.getId(), null, authorities);
//        SecurityContextHolder.getContext().setAuthentication(auth);
//    }
//
//    @Test
//    @DisplayName("통합 테스트: 배송지 저장")
//    void integration_save_test() {
//        AddressRequest request = new AddressRequest("서울시", "101", true, "집");
//        AddressResponse response = addressService.addAddress(request, testUser);
//        assertThat(response.getAdId()).isNotNull();
//    }
//}
