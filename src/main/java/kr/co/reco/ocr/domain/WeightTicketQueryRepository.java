package kr.co.reco.ocr.domain;

import static kr.co.reco.ocr.domain.QWeightTicket.weightTicket;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import kr.co.reco.ocr.application.dto.WeightTicketSearchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class WeightTicketQueryRepository {

    private final JPAQueryFactory queryFactory;

    public List<WeightTicket> search(WeightTicketSearchRequest condition) {
        return queryFactory
            .selectFrom(weightTicket)
            .where(
                containsCarNumber(condition.getCarNumber()),
                isNeedsReview(condition.getNeedsReview()),
                betweenScaledAt(condition.getStart(), condition.getEnd())
            )
            .orderBy(weightTicket.scaledAt.desc())
            .fetch();
    }

    private BooleanExpression containsCarNumber(String carNumber) {
        return StringUtils.hasText(carNumber) ? weightTicket.carNumber.contains(carNumber) : null;
    }

    private BooleanExpression isNeedsReview(Boolean needsReview) {
        return needsReview != null ? weightTicket.needsReview.eq(needsReview) : null;
    }

    private BooleanExpression betweenScaledAt(LocalDateTime start, LocalDateTime end) {
        if (start == null && end == null) return null;
        if (start != null && end == null) return weightTicket.scaledAt.goe(start);
        if (start == null && end != null) return weightTicket.scaledAt.loe(end);

        return weightTicket.scaledAt.between(start, end);
    }


}
