package kr.co.reco.ocr.domain;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@JsonPropertyOrder({ "id", "carNumber", "grossWeight", "tareWeight", "netWeight", "scaledAt", "confidence", "needsReview", "createdAt" })
public class WeightTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String carNumber;

    private Double grossWeight;  // 총중량
    private Double tareWeight;   // 공차중량 (차중량)
    private Double netWeight;    // 실중량

    private LocalDateTime scaledAt; // 계량 일시

    private Double confidence;      // OCR 전체 신뢰도
    private boolean needsReview;    // 임계값 미만일 경우 true
    private String reviewNote;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private WeightTicket(String carNumber, Double grossWeight, Double tareWeight, Double netWeight,
        LocalDateTime scaledAt, Double confidence, boolean needsReview, String reviewNote) {
        this.carNumber = carNumber;
        this.grossWeight = grossWeight;
        this.tareWeight = tareWeight;
        this.netWeight = netWeight;
        this.scaledAt = scaledAt;
        this.confidence = confidence;
        this.needsReview = needsReview;
        this.reviewNote = reviewNote;
    }

    public static WeightTicket create(String carNumber, Double grossWeight, Double tareWeight,
        Double netWeight, LocalDateTime scaledAt, Double confidence) {

        ReviewStatus status = validate(carNumber, scaledAt, grossWeight, netWeight, confidence);

        return new WeightTicket(
            carNumber, grossWeight, tareWeight, netWeight,
            scaledAt, confidence, status.needsReview(), status.reviewNote()
        );
    }

    private static ReviewStatus validate(String carNumber, LocalDateTime scaledAt, Double grossWeight,
        Double netWeight, Double confidence) {
        List<String> reasons = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        if (carNumber == null || "UNKNOWN".equals(carNumber)) reasons.add("차량번호 누락");
        if (scaledAt == null) reasons.add("계량일시 누락");
        if (grossWeight == null || grossWeight == 0.0) reasons.add("중량 정보 부족");
        if (confidence != null && confidence < 0.6) {
            reasons.add(String.format("낮은 신뢰도(%.2f)", confidence));
        }
        if (scaledAt != null && scaledAt.isAfter(now)) {
            reasons.add("계량시간 이상(미래 시간)");
        }
        if (isWeightInvalid(grossWeight, netWeight)) {
            reasons.add("중량 수치 이상(총중량 <= 실중량)");
        }

        return new ReviewStatus(!reasons.isEmpty(), String.join(", ", reasons));
    }

    private static boolean isWeightInvalid(Double gross, Double net) {
        return gross != null && net != null && gross > 0 && net > 0 && gross <= net;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    private record ReviewStatus(boolean needsReview, String reviewNote) {}
}
