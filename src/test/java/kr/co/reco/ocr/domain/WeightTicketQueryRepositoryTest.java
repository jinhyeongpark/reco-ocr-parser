package kr.co.reco.ocr.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import kr.co.reco.ocr.application.dto.WeightTicketSearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class WeightTicketQueryRepositoryTest {

    @Autowired
    private WeightTicketRepository repository;

    @Autowired
    private JPAQueryFactory queryFactory;

    private WeightTicketQueryRepository queryRepository;

    @BeforeEach
    void setUp() {
        queryRepository = new WeightTicketQueryRepository(queryFactory);

        repository.save(WeightTicket.builder()
            .carNumber("12가3456").needsReview(false).grossWeight(10000.0)
            .scaledAt(LocalDateTime.of(2026, 2, 1, 10, 0)).build());

        repository.save(WeightTicket.builder()
            .carNumber("78나9012").needsReview(true).grossWeight(20000.0)
            .scaledAt(LocalDateTime.of(2026, 2, 5, 10, 0)).build());
    }

    @Test
    @DisplayName("동적 쿼리: 차량번호 부분 일치 검색")
    void searchByCarNumber() {
        // given
        WeightTicketSearchRequest cond = new WeightTicketSearchRequest();
        cond.setCarNumber("3456");

        // when
        List<WeightTicket> result = queryRepository.search(cond);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCarNumber()).isEqualTo("12가3456");
    }

    @Test
    @DisplayName("동적 쿼리: 검토 필요 대상만 필터링")
    void searchByNeedsReview() {
        // given
        WeightTicketSearchRequest cond = new WeightTicketSearchRequest();
        cond.setNeedsReview(true);

        // when
        List<WeightTicket> result = queryRepository.search(cond);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).isNeedsReview()).isTrue();
    }
}
