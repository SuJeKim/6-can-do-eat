package com.team6.backend.payment;

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
import com.team6.backend.order.domain.OrderStatus;
import com.team6.backend.order.domain.entity.Order;
import com.team6.backend.order.domain.repository.OrderRepository;
import com.team6.backend.payment.application.PaymentService;
import com.team6.backend.payment.domain.PaymentRepository;
import com.team6.backend.payment.infrastructure.TossPaymentClient;
import com.team6.backend.payment.presetation.dto.PaymentConfirmRequest;
import com.team6.backend.payment.presetation.dto.PaymentResponse;
import com.team6.backend.store.domain.entity.Store;
import com.team6.backend.store.domain.repository.StoreRepository;
import com.team6.backend.user.domain.entity.Role;
import com.team6.backend.user.domain.entity.User;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

@Disabled("CI H2 환경에서 PostgreSQL native upsert를 지원하지 않아 PostgreSQL 테스트 환경 전환 전까지 비활성화")
@SpringBootTest
@ActiveProfiles("test")
class PaymentIntegrityTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

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

    @MockitoBean
    private RedisService redisService;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private TossPaymentClient tossPaymentClient;

    @Test
    @DisplayName("멱등성 요구사항 - 같은 결제 승인 요청 재시도는 기존 결제를 반환해야 한다")
    void confirmPayment_shouldReturnExistingPayment_whenSameRequestIsRetried() {
        Order order = savePendingOrder(15_000L);
        PaymentConfirmRequest request = paymentRequest(order.getId(), "payment-key-" + UUID.randomUUID(), 15_000L);

        PaymentResponse first = paymentService.confirmPayment(order.getId(), request);
        PaymentResponse second = paymentService.confirmPayment(order.getId(), request);

        assertThat(second.getPaymentId()).isEqualTo(first.getPaymentId());
        assertThat(paymentCountByOrderId(order.getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("동시성 요구사항 - 같은 결제 승인 요청이 동시에 들어와도 모두 같은 결제를 반환해야 한다")
    void confirmPayment_shouldReturnSamePayment_whenSameRequestArrivesConcurrently() throws Exception {
        Order order = savePendingOrder(15_000L);
        String paymentKey = "payment-key-" + UUID.randomUUID();
        List<PaymentResponse> responses = new CopyOnWriteArrayList<>();

        List<Throwable> failures = runConcurrently(10, ignored -> {
            PaymentConfirmRequest request = paymentRequest(order.getId(), paymentKey, 15_000L);
            responses.add(paymentService.confirmPayment(order.getId(), request));
        });

        assertThat(failures).isEmpty();
        assertThat(responses).hasSize(10);
        assertThat(responses.stream().map(PaymentResponse::getPaymentId).distinct().count()).isEqualTo(1L);
        assertThat(paymentCountByOrderId(order.getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("동시성 요구사항 - 같은 주문에 서로 다른 결제 키가 동시에 들어와도 결제는 하나만 성공해야 한다")
    void confirmPayment_shouldAllowOnlyOnePayment_whenDifferentPaymentKeysRaceForSameOrder() throws Exception {
        Order order = savePendingOrder(15_000L);

        List<Throwable> failures = runConcurrently(10, index -> {
            String paymentKey = "payment-key-" + index + "-" + UUID.randomUUID();
            PaymentConfirmRequest request = paymentRequest(order.getId(), paymentKey, 15_000L);
            paymentService.confirmPayment(order.getId(), request);
        });

        long successCount = 10L - failures.size();
        Order reloadedOrder = orderRepository.findById(order.getId()).orElseThrow();

        assertThat(successCount).isEqualTo(1L);
        assertThat(paymentCountByOrderId(order.getId())).isEqualTo(1L);
        assertThat(reloadedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    private Order savePendingOrder(Long totalPrice) {
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

        Order order = Order.createOrder(UUID.randomUUID(), customer, store, address, "요청사항");
        order.updateTotalPrice(totalPrice);
        return orderRepository.saveAndFlush(order);
    }

    private PaymentConfirmRequest paymentRequest(UUID orderId, String paymentKey, Long amount) {
        PaymentConfirmRequest request = new PaymentConfirmRequest();
        ReflectionTestUtils.setField(request, "paymentKey", paymentKey);
        ReflectionTestUtils.setField(request, "orderId", orderId.toString());
        ReflectionTestUtils.setField(request, "amount", amount);
        return request;
    }

    private long paymentCountByOrderId(UUID orderId) {
        return paymentRepository.findAll().stream()
                .filter(payment -> payment.getOrder().getId().equals(orderId))
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

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T value) throws Exception;
    }
}
