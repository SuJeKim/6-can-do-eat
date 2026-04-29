package com.team6.backend.user.presentation.controller;

import com.team6.backend.global.infrastructure.response.CommonSuccessCode;
import com.team6.backend.global.infrastructure.response.SuccessResponse;
import com.team6.backend.user.application.service.UserService;
import com.team6.backend.user.presentation.dto.request.UserInfoRequest;
import com.team6.backend.user.presentation.dto.response.UserInfoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    /**
     * 사용자 상세 조회
     */
    @GetMapping("/{usernId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SuccessResponse<UserInfoResponse>> getUserDetail(@PathVariable UUID usernId) {
        UserInfoResponse response = userService.getUserDetail(usernId);
        return ResponseEntity.ok(SuccessResponse.ok(response));
    }

    /**
     * 사용자 목록 조회
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'MASTER')")
    public ResponseEntity<SuccessResponse<Page<UserInfoResponse>>> getUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "false") boolean isAsc
    ) {
        Page<UserInfoResponse> responses = userService.getUsers(keyword, page, size, sortBy, isAsc);
        return ResponseEntity.ok(SuccessResponse.ok(responses));
    }

    /**
     * 사용자 정보 수정 (닉네임, 비밀번호)
     */
    @PutMapping("/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SuccessResponse<UserInfoResponse>> updateUser(
            @PathVariable UUID userId,
            @RequestBody UserInfoRequest request
    ) {
        UserInfoResponse response = userService.updateUser(userId, request);
        return ResponseEntity.ok(SuccessResponse.of(CommonSuccessCode.OK, "정보 수정이 완료되었습니다.", response));
    }

    /**
     * 사용자 권한 변경
     */
    @PatchMapping("/{userId}/role")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<SuccessResponse<UserInfoResponse>> updateUserRole(
            @PathVariable UUID userId,
            @RequestBody UserInfoRequest request
    ) {
        UserInfoResponse response = userService.updateUserRole(userId, request.getRole());
        return ResponseEntity.ok(SuccessResponse.of(CommonSuccessCode.OK, "권한 변경이 완료되었습니다.", response));
    }

    /**
     * 사용자 삭제 (소프트)
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'MASTER')")
    public ResponseEntity<SuccessResponse<Void>> deleteUser(@PathVariable UUID userId) {
        userService.deleteUser(userId);
        return ResponseEntity.ok(SuccessResponse.of(CommonSuccessCode.OK, "사용자 삭제가 완료되었습니다.", null));
    }
}
