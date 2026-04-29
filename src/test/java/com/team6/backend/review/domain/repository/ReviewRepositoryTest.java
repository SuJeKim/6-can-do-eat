package com.team6.backend.review.domain.repository;

import com.team6.backend.address.domain.entity.Address;
import com.team6.backend.address.domain.repository.AddressRepository;
import com.team6.backend.address.presentation.dto.request.AddressRequest;
import com.team6.backend.area.domain.entity.Area;
import com.team6.backend.area.domain.repository.AreaRepository;
import com.team6.backend.category.domain.entity.Category;
import com.team6.backend.category.domain.repository.CategoryRepository;
import com.team6.backend.global.infrastructure.config.AuditorConfig;
import com.team6.backend.global.infrastructure.config.JpaAuditingConfig;
import com.team6.backend.order.domain.entity.Order;
import com.team6.backend.order.domain.repository.OrderRepository;
import com.team6.backend.review.domain.entity.Review;
import com.team6.backend.store.domain.entity.Store;
import com.team6.backend.store.domain.repository.StoreRepository;
import com.team6.backend.user.domain.entity.Role;
import com.team6.backend.user.domain.entity.User;
import com.team6.backend.auth.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({JpaAuditingConfig.class, AuditorConfig.class})
class ReviewRepositoryTest {

    @Autowired private ReviewRepository reviewRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private AreaRepository areaRepository;
    @Autowired private AddressRepository addressRepository;

    private User customer;
    private Store store;
    private Order order;

    @BeforeEach
    void setUp() {
        customer = userRepository.save(new User("tester", "password", Role.CUSTOMER, "테스터"));
        Category category = categoryRepository.save(new Category("치킨"));
        Area area = areaRepository.save(new Area("서울", "_", "_", true));
        store = storeRepository.save(new Store(customer, category, area, "맛있는 치킨집", "서울시 강남구"));
        AddressRequest addrReq = new AddressRequest("서울시 강남구", "101호", false, "우리집");
        Address address = addressRepository.save(new Address(addrReq, customer));
        order = orderRepository.save(Order.createOrder(UUID.randomUUID(), customer, store, address, "빨리 배달해주세요"));
    }

    @Test
    @DisplayName("성공: 주문 ID로 리뷰 존재 여부를 확인한다.")
    void existsByOrder_Id_Success() {
        // given
        Review review = new Review();
        review.createReview(order, 5, "최고예요!");
        reviewRepository.save(review);

        // when
        boolean exists = reviewRepository.existsByOrder_Id(order.getId());

        // then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("성공: 특정 가게의 삭제되지 않은 리뷰를 페이징 조회한다.")
    void findByStore_StoreIdAndDeletedAtIsNull_Success() {
        // given
        Review review = new Review();
        review.createReview(order, 4, "맛있어요");
        reviewRepository.save(review);

        // when
        Page<Review> reviewPage = reviewRepository.findByStore_StoreIdAndDeletedAtIsNull(
                store.getStoreId(), PageRequest.of(0, 10));

        // then
        assertThat(reviewPage.getContent()).hasSize(1);
        assertThat(reviewPage.getContent().get(0).getContent()).isEqualTo("맛있어요");
    }

    @Test
    @DisplayName("성공: 특정 가게의 리뷰 평균 별점을 계산한다.")
    void calculateAverageRatingByStoreId_Success() {
        // given: 리뷰 2개 생성 (각기 다른 주문 필요)
        Order order2 = orderRepository.save(Order.createOrder(UUID.randomUUID(), customer, store, order.getAddress(), "두번째 주문"));

        Review r1 = new Review();
        r1.createReview(order, 5, "5점 리뷰");
        reviewRepository.save(r1);

        Review r2 = new Review();
        r2.createReview(order2, 3, "3점 리뷰");
        reviewRepository.save(r2);

        // when
        Double avgRating = reviewRepository.calculateAverageRatingByStoreId(store.getStoreId());

        // then
        assertThat(avgRating).isEqualTo(4.0);
    }

    @Test
    @DisplayName("성공: 새로운 리뷰를 저장한다.")
    void save_Review_Success() {
        // given
        Review review = new Review();
        review.createReview(order, 5, "정말 맛있어요!");

        // when
        Review savedReview = reviewRepository.save(review);

        // then
        assertThat(savedReview.getId()).isNotNull();
        assertThat(savedReview.getRating()).isEqualTo(5);
    }

    @Test
    @DisplayName("실패: 존재하지 않는 주문 ID로 조회 시 false를 반환한다.")
    void existsByOrder_Id_NotFound_ReturnsFalse() {
        boolean exists = reviewRepository.existsByOrder_Id(UUID.randomUUID());
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("실패: 리뷰가 없는 가게의 페이징 조회 시 빈 결과를 반환한다.")
    void findByStore_EmptyReview_ReturnsEmptyPage() {
        Page<Review> page = reviewRepository.findByStore_StoreIdAndDeletedAtIsNull(
                store.getStoreId(), PageRequest.of(0, 10));
        assertThat(page.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("실패: 삭제된 리뷰만 있는 가게의 페이징 조회 시 빈 결과를 반환한다.")
    void findByStore_AllDeleted_ReturnsEmptyPage() {
        // given
        Review review = new Review();
        review.createReview(order, 5, "삭제될 리뷰");
        review.delete("admin"); // markDeleted 호출됨
        reviewRepository.save(review);

        // when
        Page<Review> page = reviewRepository.findByStore_StoreIdAndDeletedAtIsNull(
                store.getStoreId(), PageRequest.of(0, 10));

        // then
        assertThat(page.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("실패: 리뷰가 없는 가게의 평균 별점은 0.0을 반환한다.")
    void calculateAverageRating_NoReview_ReturnsZero() {
        Double avg = reviewRepository.calculateAverageRatingByStoreId(store.getStoreId());
        assertThat(avg).isEqualTo(0.0);
    }

    @Test
    @DisplayName("실패: 삭제된 리뷰만 있는 가게의 평균 별점은 0.0을 반환한다.")
    void calculateAverageRating_AllDeleted_ReturnsZero() {
        Review review = new Review();
        review.createReview(order, 5, "삭제될 리뷰");
        review.delete("admin");
        reviewRepository.save(review);

        Double avg = reviewRepository.calculateAverageRatingByStoreId(store.getStoreId());
        assertThat(avg).isEqualTo(0.0);
    }

    @Test
    @DisplayName("실패: 별점이 1점 미만일 경우 생성 시 예외가 발생한다.")
    void createReview_RatingUnderOne_ThrowsException() {
        Review review = new Review();
        assertThatThrownBy(() -> review.createReview(order, 0, "나빠요"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("별점 1점에서 5점 사이만 가능합니다.");
    }

    @Test
    @DisplayName("실패: 별점이 5점을 초과할 경우 생성 시 예외가 발생한다.")
    void createReview_RatingOverFive_ThrowsException() {
        Review review = new Review();
        assertThatThrownBy(() -> review.createReview(order, 6, "너무 좋아요"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("별점 1점에서 5점 사이만 가능합니다.");
    }

    @Test
    @DisplayName("실패: 필수값인 별점(rating)이 누락된 경우 저장이 실패한다.")
    void save_NullRating_ThrowsException() {
        // createReview를 거치지 않고 강제로 set 하거나, DB 제약조건 확인
        // Review의 필드 @Column(nullable = false) 기반
        Review review = new Review();
        // createReview를 안쓰고 직접 필드 채우기는 어려우나, Reflection 등으로 null 주입 가정 시
        assertThatThrownBy(() -> reviewRepository.saveAndFlush(review))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("실패: 필수값인 주문(order)이 누락된 경우 저장이 실패한다.")
    void save_NullOrder_ThrowsException() {
        Review review = new Review();
        // rating만 있고 order가 없는 경우
        assertThatThrownBy(() -> reviewRepository.saveAndFlush(review))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("실패: 동일한 주문(order_id)에 대해 중복 리뷰 저장 시 유니크 제약 조건 위반이 발생한다.")
    void save_DuplicateOrderReview_ThrowsException() {
        // given: 첫 번째 리뷰 저장
        Review r1 = new Review();
        r1.createReview(order, 5, "첫 리뷰");
        reviewRepository.save(r1);

        // when & then: 동일한 order로 두 번째 리뷰 저장 시도 (OneToOne unique=true)
        Review r2 = new Review();
        r2.createReview(order, 3, "중복 리뷰");

        assertThatThrownBy(() -> reviewRepository.saveAndFlush(r2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
