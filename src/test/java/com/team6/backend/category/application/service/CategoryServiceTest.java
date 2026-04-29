package com.team6.backend.category.application.service;

import com.team6.backend.category.domain.entity.Category;
import com.team6.backend.category.domain.repository.CategoryRepository;
import com.team6.backend.category.presentation.dto.request.CategoryRequest;
import com.team6.backend.category.presentation.dto.response.CategoryResponse;
import com.team6.backend.global.infrastructure.config.security.util.SecurityUtils;
import com.team6.backend.global.infrastructure.exception.ApplicationException;
import com.team6.backend.category.domain.exception.CategoryErrorCode;
import com.team6.backend.global.infrastructure.util.AuthValidator;
import com.team6.backend.store.domain.repository.StoreRepository;
import com.team6.backend.user.domain.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private SecurityUtils securityUtils;
    @Mock private AuthValidator authValidator;

    @InjectMocks private CategoryService categoryService;

    private CategoryRequest createRequest(String name) {
        CategoryRequest request = new CategoryRequest();
        ReflectionTestUtils.setField(request, "name", name);
        return request;
    }

    @Test
    @DisplayName("카테고리 생성 성공 - 권한 및 중복 체크 통과")
    void createCategory_Success() {
        // given
        CategoryRequest request = createRequest("한식");
        Category category = new Category("한식");
        ReflectionTestUtils.setField(category, "categoryId", UUID.randomUUID());

        doNothing().when(authValidator).validateAccess(any(), any(), any(), any());
        given(categoryRepository.existsByName("한식")).willReturn(false);
        given(categoryRepository.save(any(Category.class))).willReturn(category);

        // when
        CategoryResponse response = categoryService.createCategory(request);

        // then
        assertThat(response.getName()).isEqualTo("한식");
        verify(authValidator).validateAccess(isNull(), eq(List.of(Role.MASTER, Role.MANAGER)), isNull(), eq(CategoryErrorCode.CATEGORY_FORBIDDEN));
    }

    @Test
    @DisplayName("카테고리 생성 실패 - 권한 부족 (FORBIDDEN_ACCESS)")
    void createCategory_Fail_Forbidden() {
        // given
        CategoryRequest request = createRequest("신규");
        willThrow(new ApplicationException(CategoryErrorCode.CATEGORY_FORBIDDEN))
                .given(authValidator).validateAccess(any(), any(), any(), any());

        // when & then
        assertThatThrownBy(() -> categoryService.createCategory(request))
                .isInstanceOf(ApplicationException.class)
                .hasMessage(CategoryErrorCode.CATEGORY_FORBIDDEN.getMessage());
    }

    @Test
    @DisplayName("카테고리 수정 실패 - 권한 부족")
    void updateCategory_Fail_Forbidden() {
        // given
        UUID id = UUID.randomUUID();
        CategoryRequest request = createRequest("수정");
        willThrow(new ApplicationException(CategoryErrorCode.CATEGORY_FORBIDDEN))
                .given(authValidator).validateAccess(any(), any(), any(), any());

        // when & then
        assertThatThrownBy(() -> categoryService.updateCategory(id, request))
                .isInstanceOf(ApplicationException.class)
                .hasMessage(CategoryErrorCode.CATEGORY_FORBIDDEN.getMessage());
    }

    @Test
    @DisplayName("카테고리 수정 성공 - 본인 이름과 동일하게 수정 시 중복체크 생략")
    void updateCategory_SameName_Success() {
        // given
        UUID id = UUID.randomUUID();
        Category category = new Category("기존이름");
        CategoryRequest request = createRequest("기존이름");

        doNothing().when(authValidator).validateAccess(any(), any(), any(), any());
        given(categoryRepository.findById(id)).willReturn(Optional.of(category));

        // when
        categoryService.updateCategory(id, request);

        // then
        verify(categoryRepository).findById(id);
        // existsByName이 호출되지 않았는지 검증하여 로직 효율성 확인
        verify(categoryRepository, org.mockito.Mockito.never()).existsByName(anyString());
    }

    @Test
    @DisplayName("카테고리 삭제 성공 - MASTER 권한 확인")
    void deleteCategory_Success() {
        // given
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Category category = new Category("삭제용");

        doNothing().when(authValidator).validateAccess(any(), any(), any(), any());
        given(categoryRepository.findById(id)).willReturn(Optional.of(category));
        given(securityUtils.getCurrentUserId()).willReturn(userId);

        // when
        categoryService.deleteCategory(id);

        // then
        assertThat(category.isDeleted()).isTrue();
        // 삭제 권한 파라미터(MASTER 전용)가 정확히 전달되었는지 확인
        verify(authValidator).validateAccess(isNull(), eq(List.of(Role.MASTER)), isNull(), eq(CategoryErrorCode.CATEGORY_FORBIDDEN));
    }

    @Test
    @DisplayName("카테고리 삭제 실패 - 존재하지 않는 카테고리 (404)")
    void deleteCategory_NotFound() {
        // given
        UUID id = UUID.randomUUID();
        doNothing().when(authValidator).validateAccess(any(), any(), any(), any());
        given(categoryRepository.findById(id)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> categoryService.deleteCategory(id))
                .isInstanceOf(ApplicationException.class)
                .hasMessage(CategoryErrorCode.CATEGORY_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("카테고리 삭제 실패 - 이미 사용 중인 카테고리 (400)")
    void deleteCategory_Fail_WhenCategoryIsInUse() {
        // given
        UUID categoryId = UUID.randomUUID();
        given(storeRepository.existsByCategory_CategoryId(categoryId)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> categoryService.deleteCategory(categoryId))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining(CategoryErrorCode.CATEGORY_IN_USE.getMessage());

        verify(storeRepository).existsByCategory_CategoryId(categoryId);
    }

}
