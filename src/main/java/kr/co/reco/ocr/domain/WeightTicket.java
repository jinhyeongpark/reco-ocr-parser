package kr.co.reco.ocr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import java.time.LocalDateTime;
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
public class WeightTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String documentType; // 증명서, 확인서, 계량표 등 정규화된 값
    private String vendorName;   // 발행처 (동우 바이오, 하은펄프)

    @Column(nullable = false)
    private String carNumber;

    private Double grossWeight;  // 총중량
    private Double tareWeight;   // 공차중량 (차중량)
    private Double netWeight;    // 실중량

    private LocalDateTime scaledAt; // 계량 일시

    private Double confidence;      // OCR 전체 신뢰도
    private boolean needsReview;    // 임계값 미만일 경우 true

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
