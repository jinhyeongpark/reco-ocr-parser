package kr.co.reco.ocr.application.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OcrResult {
    private final String fullText;
    private final Double confidence;

}
