package kr.co.reco.ocr.application.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WeightTicketUpdateRequest {
    private String carNumber;
    private Double grossWeight;
    private Double tareWeight;
    private Double netWeight;
    private LocalDateTime scaledAt;
}
