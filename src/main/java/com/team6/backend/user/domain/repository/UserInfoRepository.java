package com.team6.backend.user.domain.repository;

import com.team6.backend.user.domain.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserInfoRepository extends JpaRepository<User, UUID> {
    
    // 유저네임으로 상세 조회 (삭제 안 된 사람만)
    Optional<User> findByUsernameAndDeletedAtIsNull(String username);

    // 전체 목록 조회 (삭제 안 된 사람만)
    Page<User> findAllByDeletedAtIsNull(Pageable pageable);

    // 검색: username 또는 nickname 포함 (삭제 안 된 사람만)
    Page<User> findAllByDeletedAtIsNullAndUsernameContainingOrNicknameContainingAndDeletedAtIsNull(
            String username, String nickname, Pageable pageable);

    Optional<User> findByUsername(String username);

    // ID로 상세 조회 (삭제 안 된 사람만)
    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByUsername(String newUsername);
}
