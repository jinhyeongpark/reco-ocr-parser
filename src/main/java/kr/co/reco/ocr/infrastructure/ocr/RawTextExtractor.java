package kr.co.reco.ocr.infrastructure.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.File;
import kr.co.reco.ocr.application.dto.OcrResult;
import kr.co.reco.ocr.global.error.CustomException;
import kr.co.reco.ocr.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class RawTextExtractor {

    private final ObjectMapper objectMapper;

    public OcrResult extract(String filePath) {
        File jsonFile = new File(filePath);

        if (!jsonFile.exists()) {
            throw new CustomException(ErrorCode.SAMPLE_FILE_NOT_FOUND);
        }

        try {
            JsonNode root = objectMapper.readTree(jsonFile);

            // 구글 OCR JSON 구조: 최하단 text 필드와 최상단 confidence 필드 추출
            String fullText = root.path("text").asText();
            Double confidence = root.path("confidence").asDouble();

            if (fullText == null || fullText.isBlank()) {
                throw new CustomException(ErrorCode.OCR_PARSING_FAILED);
            }

            return OcrResult.builder()
                .fullText(fullText)
                .confidence(confidence)
                .build();

        } catch (Exception e) {
            log.error("OCR JSON Parsing Error: {}", filePath, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

}
