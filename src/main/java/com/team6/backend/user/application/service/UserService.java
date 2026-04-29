package com.team6.backend.user.application.service;

import com.team6.backend.auth.application.service.TokenService;
import com.team6.backend.global.infrastructure.config.security.util.SecurityUtils;
import com.team6.backend.global.infrastructure.exception.ApplicationException;
import com.team6.backend.global.infrastructure.exception.CommonErrorCode;
import com.team6.backend.global.infrastructure.redis.RedisService;
import com.team6.backend.user.domain.entity.Role;
import com.team6.backend.user.domain.entity.User;
import com.team6.backend.user.domain.exception.UserErrorCode;
import com.team6.backend.user.domain.repository.UserInfoRepository;
import com.team6.backend.user.presentation.dto.request.UserInfoRequest;
import com.team6.backend.user.presentation.dto.response.UserInfoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserInfoRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RedisService redisService;
    private final SecurityUtils securityUtils;

    /**
     * 사용자 상세 조회
     */
    @Transactional(readOnly = true)
    public UserInfoResponse getUserDetail(UUID userId) {
        // 본인 또는 MASTER 권한 체크 추가
        validateOwnership(userId);
        return UserInfoResponse.from(getUserById(userId));
    }

    /**
     * 사용자 목록 조회
     */
    @Transactional(readOnly = true)
    public Page<UserInfoResponse> getUsers(String keyword, int page, int size, String sortBy, boolean isAsc) {
        Sort sort = isAsc ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<User> userPage;
        if (keyword != null && !keyword.isBlank()) {
            userPage = userRepository.findAllByDeletedAtIsNullAndUsernameContainingOrNicknameContainingAndDeletedAtIsNull(
                    keyword, keyword, pageable);
        } else {
            userPage = userRepository.findAllByDeletedAtIsNull(pageable);
        }

        return userPage.map(UserInfoResponse::from);
    }

    /**
     * 사용자 정보 수정
     */
    @Transactional
    public UserInfoResponse updateUser(UUID userId, UserInfoRequest request) {
        // 1. 권한 체크 (본인 또는 MASTER)
        validateOwnership(userId);

        // 2. 대상 조회
        User targetUser = getUserById(userId);

        // 3. 수정
        targetUser.updateUsername(request.getUsername());

        // 닉네임(username) 변경 및 검증
        updateUsernameIfChanged(targetUser, request.getUsername());

        // 비밀번호 변경 및 검증
        updatePasswordIfProvided(targetUser, request.getPassword());

        return UserInfoResponse.from(targetUser);
    }

    /**
     * 사용자 권한 변경 (MASTER 전용)
     */
    @Transactional
    public UserInfoResponse updateUserRole(UUID userId, Role newRole) {
        // 1. MASTER 권한 체크
        if (!securityUtils.getCurrentUserRole().equals(Role.MASTER)) {
            throw new ApplicationException(CommonErrorCode.FORBIDDEN);
        }

        // 2. 대상 조회
        User targetUser = getUserById(userId);

        // 3. 동일 권한 변경 체크
        if (targetUser.getRole().equals(newRole)) {
            throw new ApplicationException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        // 4. 본인 권한 변경 금지
        if (targetUser.getId().equals(securityUtils.getCurrentUserId())) { // 현재 로그인한 사용자의 ID와 비교
            throw new ApplicationException(CommonErrorCode.FORBIDDEN);
        }

        // 5. 업데이트
        targetUser.updateRole(newRole);
        userRepository.saveAndFlush(targetUser);

        // 6. 리프레시 토큰 삭제
        tokenService.deleteRefreshToken(targetUser.getId());

        /*
          7. Redis role 캐시 업데이트 (30분 고정)
          TTL을 액세스 토큰 만료 시간과 통일하려면 JwtUtil 주입이 필요하지만,
          UserService에 JWT 관련 의존성이 생기기 때문에 JwtFilter와 동일한 30분으로 고정
          TODO: 공통 상수로 관리 필요
         */
        redisService.set("role:" + targetUser.getId(), newRole.name(), Duration.ofMinutes(30));

        return UserInfoResponse.from(targetUser);
    }

    /**
     * 실시간 권한 검증 유틸
     */
    @Transactional(readOnly = true)
    public void validateUserRole(UUID userId, Role requiredRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationException(CommonErrorCode.RESOURCE_NOT_FOUND));

        if (!user.getRole().equals(requiredRole)) {
            throw new ApplicationException(CommonErrorCode.FORBIDDEN);
        }
    }

    /**
     * 사용자 삭제(소프트)
     */
    @Transactional
    public void deleteUser(UUID userId) {
        // 1. 권한 체크 (본인 또는 MASTER)
        validateOwnership(userId);

        // 2. 대상 조회
        User targetUser = getUserById(userId);

        // 3. 삭제 처리
        targetUser.markDeleted(securityUtils.getCurrentUserId().toString()); // 현재 로그인한 사용자의 ID로 기록

        // 4. 리프레시 토큰 삭제 (재로그인 불가)
        tokenService.deleteRefreshToken(targetUser.getId());

        // 5. Redis role 캐시 삭제
        redisService.delete("role:" + targetUser.getId());
    }

    private User getUserById(UUID userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApplicationException(CommonErrorCode.RESOURCE_NOT_FOUND));
    }

    private void validateOwnership(UUID userId) {
        UUID currentUserId = securityUtils.getCurrentUserId();
        Role currentUserRole = securityUtils.getCurrentUserRole();

        if (!currentUserRole.equals(Role.MASTER) && !userId.equals(currentUserId)) {
            throw new ApplicationException(CommonErrorCode.FORBIDDEN);
        }
    }

    /**
     * 닉네임(username) 변경 시 중복 및 동일 여부 확인 후 변경
     */
    private void updateUsernameIfChanged(User targetUser, String newUsername) {
        if (newUsername == null || newUsername.isBlank()) return;
        
        // 현재 이름과 동일한 경우
        if (targetUser.getUsername().equals(newUsername)) {
            return; // 변경하지 않음 (또는 필요시 에러 발생 가능)
        }

        // 다른 사용자가 사용 중인지 확인
        if (userRepository.existsByUsername(newUsername)) {
            throw new ApplicationException(UserErrorCode.DUPLICATE_USERNAME);
        }
        
        targetUser.updateUsername(newUsername);
    }

    /**
     * 비밀번호 변경 시 기존 비밀번호와 동일 여부 확인 후 변경
     */
    private void updatePasswordIfProvided(User targetUser, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) return;

        // 기존 비밀번호와 동일한지 확인
        if (passwordEncoder.matches(newPassword, targetUser.getPassword())) {
            throw new ApplicationException(UserErrorCode.PASSWORD_ALREADY_IN_USE);
        }

        targetUser.updatePassword(passwordEncoder.encode(newPassword));
    }
}
