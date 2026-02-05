package kr.co.reco.ocr.infrastructure.ocr;

import java.io.File;
import kr.co.reco.ocr.application.dto.OcrResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class RawTextExtractor {

    private final ObjectMapper objectMapper;

    public OcrResult extract(String filePath) {
        File jsonFile = new File(filePath);

        try {
            JsonNode root = objectMapper.readTree(jsonFile);

            // 구글 OCR JSON 구조: 최하단 text 필드와 최상단 confidence 필드 추출
            String fullText = root.path("text").asText();
            Double confidence = root.path("confidence").asDouble();

            return OcrResult.builder()
                .fullText(fullText)
                .confidence(confidence)
                .build();

        } catch (Exception e) {
            throw new RuntimeException("OCR JSON 파일 읽기 실패: " + filePath, e);
        }
    }

}
