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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final StoreRepository storeRepository;
    private final SecurityUtils securityUtils;
    private final AuthValidator authValidator;

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        authValidator.validateAccess(
                null,
                List.of(Role.MASTER, Role.MANAGER),
                null,
                CategoryErrorCode.CATEGORY_FORBIDDEN
        );

        // 카테고리 이름이 중복인지 확인
        if (categoryRepository.existsByName(request.getName())) {
            log.warn("[CATEGORY] 카테고리 생성 실패: 중복된 이름. name: {}", request.getName());
            throw new ApplicationException(CategoryErrorCode.DUPLICATE_CATEGORY_NAME);
        }

        Category category = new Category(request.getName());
        Category saved = categoryRepository.save(category);
        return new CategoryResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<CategoryResponse> getCategories(String keyword, int page, int size, String sortBy, boolean isAsc) {
        Sort.Direction direction = isAsc ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Category> categoryPage;
        if (keyword != null && !keyword.isBlank()) {
            categoryPage = categoryRepository.findByNameContainingIgnoreCase(keyword, pageable);
        } else {
            categoryPage = categoryRepository.findAll(pageable);
        }

        return categoryPage.map(CategoryResponse::new);
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(UUID categoryId) {
        Category category = findCategoryById(categoryId);
        return new CategoryResponse(category);
    }

    @Transactional
    public CategoryResponse updateCategory(UUID categoryId, CategoryRequest request) {
        authValidator.validateAccess(
                null,
                List.of(Role.MASTER, Role.MANAGER),
                null,
                CategoryErrorCode.CATEGORY_FORBIDDEN
        );

        Category category = findCategoryById(categoryId);

        if (!category.getName().equals(request.getName()) && categoryRepository.existsByName(request.getName())) {
            log.warn("[CATEGORY] 카테고리 수정 실패: 중복된 이름. name: {}", request.getName());
            throw new ApplicationException(CategoryErrorCode.DUPLICATE_CATEGORY_NAME);
        }

        category.update(request.getName());
        return new CategoryResponse(category);
    }

    @Transactional
    public void deleteCategory(UUID categoryId) {
        authValidator.validateAccess(
                null,
                List.of(Role.MASTER),
                null,
                CategoryErrorCode.CATEGORY_FORBIDDEN
        );

        if (storeRepository.existsByCategory_CategoryId(categoryId)) {
            log.warn("[CATEGORY] 카테고리 삭제 실패: 사용 중인 카테고리입니다. categoryId: {}", categoryId);
            throw new ApplicationException(CategoryErrorCode.CATEGORY_IN_USE);
        }

        Category category = findCategoryById(categoryId);
        category.markDeleted(securityUtils.getCurrentUserId().toString());
    }

    private Category findCategoryById(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> {
                    log.warn("[CATEGORY] 카테고리를 찾을 수 없습니다. categoryId: {}", categoryId);
                    return new ApplicationException(CategoryErrorCode.CATEGORY_NOT_FOUND);
                });
    }

}
