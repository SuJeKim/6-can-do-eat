package com.team6.backend.order;

import com.team6.backend.address.domain.entity.Address;
import com.team6.backend.address.domain.repository.AddressRepository;
import com.team6.backend.address.presentation.dto.request.AddressRequest; // 수정된 부분
import com.team6.backend.area.domain.entity.Area;
import com.team6.backend.area.domain.repository.AreaRepository;
import com.team6.backend.auth.domain.repository.UserRepository;
import com.team6.backend.category.domain.entity.Category;
import com.team6.backend.category.domain.repository.CategoryRepository;
import com.team6.backend.global.infrastructure.config.security.jwt.JwtUtil;
import com.team6.backend.global.infrastructure.redis.RedisService;
import com.team6.backend.menu.domain.entity.Menu;
import com.team6.backend.menu.domain.repository.MenuRepository;
import com.team6.backend.order.application.OrderService;
import com.team6.backend.order.domain.OrderStatus;
import com.team6.backend.order.domain.entity.Order;
import com.team6.backend.order.domain.repository.OrderRepository;
import com.team6.backend.order.presentation.dto.OrderCreateRequest;
import com.team6.backend.order.presentation.dto.OrderItemCreateRequest;
import com.team6.backend.order.presentation.dto.OrderResponse;
import com.team6.backend.order.presentation.dto.OrderStatusUpdate;
import com.team6.backend.payment.infrastructure.TossPaymentClient;
import com.team6.backend.store.domain.entity.Store;
import com.team6.backend.store.domain.repository.StoreRepository;
import com.team6.backend.user.domain.entity.Role;
import com.team6.backend.user.domain.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OrderIntegrityTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private MenuRepository menuRepository;

    @MockitoBean
    private RedisService redisService;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private TossPaymentClient tossPaymentClient;

    @Test
    @DisplayName("동시성 요구사항 - 같은 PENDING 주문의 상태 변경은 하나만 성공해야 한다")
    void updateStatusAndCancelOrder_shouldAllowOnlyOneSuccess_whenTheyRaceForSameOrder() throws Exception {
        OrderFixture fixture = saveOrderFixture(15_000L);

        List<Throwable> failures = runConcurrently(20, index -> {
            if (index % 2 == 0) {
                orderService.cancelOrder(fixture.orderId());
                return;
            }

            orderService.updateOrderStatus(
                    fixture.orderId(),
                    fixture.ownerId(),
                    Role.OWNER,
                    statusRequest(OrderStatus.COMPLETED)
            );
        });

        long successCount = 20L - failures.size();
        Order reloadedOrder = orderRepository.findById(fixture.orderId()).orElseThrow();

        assertThat(successCount).isEqualTo(1L);
        assertThat(reloadedOrder.getStatus()).isIn(OrderStatus.CANCELLED, OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("멱등성 요구사항 - 같은 주문 생성 요청 재시도는 기존 주문을 반환해야 한다")
    void createOrder_shouldReturnExistingOrder_whenSameRequestIsRetried() {
        OrderFixture fixture = saveOrderFixtureWithoutOrder();
        OrderCreateRequest request = orderCreateRequest(
                fixture.storeId(),
                fixture.addressId(),
                "문 앞에 놓아주세요",
                orderItemCreateRequest(fixture.menuId(), 1)
        );

        OrderResponse first = orderService.createOrder(request, fixture.customerId());
        OrderResponse second = orderService.createOrder(request, fixture.customerId());

        assertThat(second.getOrderId()).isEqualTo(first.getOrderId());
        assertThat(orderCountByUserId(fixture.customerId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("동시성 요구사항 - 같은 주문 생성 요청이 동시에 들어와도 모두 같은 주문을 반환해야 한다")
    void createOrder_shouldReturnSameOrder_whenSameRequestArrivesConcurrently() throws Exception {
        OrderFixture fixture = saveOrderFixtureWithoutOrder();
        OrderCreateRequest request = orderCreateRequest(
                fixture.storeId(),
                fixture.addressId(),
                "문 앞에 놓아주세요",
                orderItemCreateRequest(fixture.menuId(), 1)
        );
        List<OrderResponse> responses = new CopyOnWriteArrayList<>();

        List<Throwable> failures = runConcurrently(10, ignored -> {
            responses.add(orderService.createOrder(request, fixture.customerId()));
        });

        assertThat(failures).isEmpty();
        assertThat(responses).hasSize(10);
        assertThat(responses.stream().map(OrderResponse::getOrderId).distinct().count()).isEqualTo(1L);
        assertThat(orderCountByUserId(fixture.customerId())).isEqualTo(1L);
    }

    private OrderFixture saveOrderFixture(Long totalPrice) {
        OrderFixture fixture = saveOrderFixtureWithoutOrder();

        Order order = Order.createOrder(
                UUID.randomUUID(),
                userRepository.findById(fixture.customerId()).orElseThrow(),
                storeRepository.findById(fixture.storeId()).orElseThrow(),
                addressRepository.findById(fixture.addressId()).orElseThrow(),
                "요청사항"
        );
        order.updateTotalPrice(totalPrice);
        Order savedOrder = orderRepository.saveAndFlush(order);

        return fixture.withOrderId(savedOrder.getId());
    }

    private OrderFixture saveOrderFixtureWithoutOrder() {
        User customer = userRepository.save(new User(
                "customer-" + UUID.randomUUID(),
                "password",
                Role.CUSTOMER,
                "고객-" + UUID.randomUUID()
        ));
        User owner = userRepository.save(new User(
                "owner-" + UUID.randomUUID(),
                "password",
                Role.OWNER,
                "사장-" + UUID.randomUUID()
        ));
        Category category = categoryRepository.save(new Category("category-" + UUID.randomUUID()));
        Area area = areaRepository.save(new Area("area-" + UUID.randomUUID(), "서울", "강남구", true));
        Store store = storeRepository.save(new Store(owner, category, area, "store-" + UUID.randomUUID(), "서울"));
        Address address = addressRepository.save(new Address(
                new AddressRequest("서울시 강남구", "101호", false, "집"),
                customer
        ));
        Menu menu = menuRepository.save(new Menu(store, "치킨", 15_000, "테스트 메뉴"));

        return new OrderFixture(
                customer.getId(),
                owner.getId(),
                store.getStoreId(),
                address.getAdId(),
                menu.getMenuId(),
                null
        );
    }

    private OrderCreateRequest orderCreateRequest(
            UUID storeId,
            UUID addressId,
            String requestText,
            OrderItemCreateRequest... itemRequests
    ) {
        OrderCreateRequest request = BeanUtils.instantiateClass(OrderCreateRequest.class);
        ReflectionTestUtils.setField(request, "idempotencyKey", UUID.randomUUID());
        ReflectionTestUtils.setField(request, "storeId", storeId);
        ReflectionTestUtils.setField(request, "addressId", addressId);
        ReflectionTestUtils.setField(request, "requestText", requestText);
        ReflectionTestUtils.setField(request, "itemRequests", List.of(itemRequests));
        return request;
    }

    private OrderItemCreateRequest orderItemCreateRequest(UUID menuId, Integer quantity) {
        OrderItemCreateRequest request = BeanUtils.instantiateClass(OrderItemCreateRequest.class);
        ReflectionTestUtils.setField(request, "menuId", menuId);
        ReflectionTestUtils.setField(request, "quantity", quantity);
        return request;
    }

    private OrderStatusUpdate.Request statusRequest(OrderStatus status) {
        OrderStatusUpdate.Request request = BeanUtils.instantiateClass(OrderStatusUpdate.Request.class);
        ReflectionTestUtils.setField(request, "orderStatus", status);
        return request;
    }

    private long orderCountByUserId(UUID userId) {
        return orderRepository.findAll().stream()
                .filter(order -> order.getUser().getId().equals(userId))
                .count();
    }

    private List<Throwable> runConcurrently(int threadCount, ThrowingConsumer<Integer> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            int index = i;
            futures.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("동시 실행 시작 대기 시간이 초과되었습니다.");
                }
                task.accept(index);
                return null;
            }));
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<Throwable> failures = new ArrayList<>();
        for (Future<?> future : futures) {
            try {
                future.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                failures.add(e.getCause() == null ? e : e.getCause());
            }
        }

        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        return failures;
    }

    private record OrderFixture(
            UUID customerId,
            UUID ownerId,
            UUID storeId,
            UUID addressId,
            UUID menuId,
            UUID orderId
    ) {
        private OrderFixture withOrderId(UUID orderId) {
            return new OrderFixture(customerId, ownerId, storeId, addressId, menuId, orderId);
        }
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T value) throws Exception;
    }
}
