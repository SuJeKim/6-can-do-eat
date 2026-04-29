package com.team6.backend.review.application.service;

import com.team6.backend.address.domain.entity.Address;
import com.team6.backend.address.domain.repository.AddressRepository;
import com.team6.backend.area.domain.entity.Area;
import com.team6.backend.area.domain.repository.AreaRepository;
import com.team6.backend.auth.domain.repository.UserRepository;
import com.team6.backend.category.domain.entity.Category;
import com.team6.backend.category.domain.repository.CategoryRepository;
import com.team6.backend.global.infrastructure.config.security.util.SecurityUtils;
import com.team6.backend.global.infrastructure.util.AuthValidator;
import com.team6.backend.order.domain.OrderStatus;
import com.team6.backend.order.domain.entity.Order;
import com.team6.backend.order.domain.repository.OrderRepository;
import com.team6.backend.review.presentation.dto.request.ReviewRequestDto;
import com.team6.backend.store.domain.entity.Store;
import com.team6.backend.store.domain.repository.StoreRepository;
import com.team6.backend.user.domain.entity.Role;
import com.team6.backend.user.domain.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

@SpringBootTest
class ReviewServiceConcurrencyTest {

    @Autowired private ReviewService reviewService;
    @Autowired private UserRepository userRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private AreaRepository areaRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private AddressRepository addressRepository;

    @MockitoBean private SecurityUtils securityUtils;
    @MockitoBean private AuthValidator authValidator;

    @Test
    @DisplayName("동시에 100개의 리뷰가 작성될 때 동시성 문제(Lost Update)로 인해 평점 갱신이 누락되면 안 된다.")
    void reviewConcurrencyTest() throws InterruptedException {
        // given
        String randomStr = UUID.randomUUID().toString().substring(0, 8);

        User owner = new User("owner_" + randomStr, "pass", Role.OWNER, "점주님");
        userRepository.save(owner);

        Category category = new Category("테스트 카테고리 " + randomStr);
        categoryRepository.save(category);

        Area area = new Area("테스트 지역 " + randomStr, "서울시", "강남구", true);
        areaRepository.save(area);

        Store store = new Store(owner, category, area, "동시성 테스트 가게 " + randomStr, "강남구 123");
        storeRepository.save(store);

        int threadCount = 100;
        List<Order> orders = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            User customer = new User("cust_" + randomStr + "_" + i, "pass", Role.CUSTOMER, "고객" + i);
            userRepository.save(customer);

            Address address = new Address();
            ReflectionTestUtils.setField(address, "user", customer);
            ReflectionTestUtils.setField(address, "address", "배송지 " + i);
            addressRepository.save(address);

            Order order = Order.createOrder(UUID.randomUUID(), customer, store, address, "조심히 와주세요");
            ReflectionTestUtils.setField(order, "status", OrderStatus.COMPLETED);
            orderRepository.save(order);

            orders.add(order);
        }

        doNothing().when(authValidator).validateAccess(any(), any(), any(), any());

        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when
        // 50명은 1점, 50명은 5점을 동시에 작성
        for (int i = 0; i < threadCount; i++) {
            final UUID orderId = orders.get(i).getId();
            final int rating = (i % 2 == 0) ? 1 : 5;

            executorService.submit(() -> {
                try {
                    ReviewRequestDto request = new ReviewRequestDto();
                    ReflectionTestUtils.setField(request, "rating", rating);
                    ReflectionTestUtils.setField(request, "content", rating + "점 드립니다!");

                    reviewService.createReview(orderId, request);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // then
        Store updatedStore = storeRepository.findById(store.getStoreId()).orElseThrow();
        System.out.println("기대하는 평점: 3.0 / 실제 반영된 평점: " + updatedStore.getRating());

        assertThat(updatedStore.getRating()).isEqualTo(3.0);
    }
}
