package kr.co.reco.ocr.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import kr.co.reco.ocr.application.dto.OcrResult;
import kr.co.reco.ocr.domain.WeightTicket;
import kr.co.reco.ocr.domain.WeightTicketRepository;
import kr.co.reco.ocr.infrastructure.ocr.RawTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
public class ParsingServiceTest {

    private RawTextExtractor extractor;
    private ParsingService parsingService;

    @Mock
    private WeightTicketRepository weightTicketRepository;
    private final String SAMPLE_PATH = "src/main/resources/sample/";

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        extractor = new RawTextExtractor(objectMapper);
        parsingService = new ParsingServiceImpl(weightTicketRepository);

        when(weightTicketRepository.save(any(WeightTicket.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    @DisplayName("샘플 json을 통한 OCR 파싱 엔진 검증")
    class ParsingLogic {
        @Test
        @DisplayName("샘플 01: 표준 계량증명서 파싱 테스트")
        void parseSample01() {
            // given
            OcrResult ocrResult = extractor.extract(SAMPLE_PATH + "sample_01.json");

            // when
            WeightTicket result = parsingService.parse(ocrResult);

            // then
            assertThat(result.getCarNumber()).isEqualTo("8713");
            assertThat(result.getGrossWeight()).isEqualTo(12480.0);
            assertThat(result.getNetWeight()).isEqualTo(5010.0);
            assertThat(result.getScaledAt()).isEqualTo(LocalDateTime.of(2026, 2, 2, 5, 37, 55));
        }

        @Test
        @DisplayName("샘플 02: 오타(계 그 표) 및 숫자 공백(13 460) 처리 테스트")
        void parseSample02() {
            // given
            OcrResult ocrResult = extractor.extract(SAMPLE_PATH + "sample_02.json");

            // when
            WeightTicket result = parsingService.parse(ocrResult);

            // then
            assertThat(result.getCarNumber()).isEqualTo("80구8713"); // 지역구분 포함
            assertThat(result.getGrossWeight()).isEqualTo(13460.0); // '13 460' 공백 제거 확인
            assertThat(result.getTareWeight()).isEqualTo(7560.0); // '차중량' 매핑 확인
            assertThat(result.getNetWeight()).isEqualTo(5900.0);
            assertThat(result.getScaledAt()).isEqualTo(LocalDateTime.of(2026, 2, 2, 2, 14, 23));
        }

        @Test
        @DisplayName("샘플 03: 계량확인서 및 한글 포함 중량 파싱 테스트")
        void parseSample03() {
            // given
            OcrResult ocrResult = extractor.extract(SAMPLE_PATH + "sample_03.json");

            // when
            WeightTicket result = parsingService.parse(ocrResult);

            // then
            assertThat(result.getCarNumber()).isEqualTo("5405");
            assertThat(result.getGrossWeight()).isEqualTo(14080.0);
            assertThat(result.getTareWeight()).isEqualTo(13950.0);
            assertThat(result.getNetWeight()).isEqualTo(130.0);
            assertThat(result.getScaledAt()).isEqualTo(LocalDateTime.of(2026, 2, 1, 11, 55, 35));
        }

        @Test
        @DisplayName("샘플 04: 계량증명표 및 괄호 포함 중량 파싱 테스트")
        void parseSample04() {
            // given
            OcrResult ocrResult = extractor.extract(SAMPLE_PATH + "sample_04.json");

            // when
            WeightTicket result = parsingService.parse(ocrResult);

            // then
            assertThat(result.getCarNumber()).isEqualTo("0580");
            assertThat(result.getGrossWeight()).isEqualTo(14230.0);
            assertThat(result.getTareWeight()).isEqualTo(12910.0);
            assertThat(result.getNetWeight()).isEqualTo(1320.0);
            assertThat(result.getScaledAt()).isEqualTo(LocalDateTime.of(2025, 12, 1, 9, 9, 0));
        }
    }

    @Nested
    @DisplayName("데이터 검토 플래그(needsReview) 판정 로직")
    class ReviewFlagLogic {
        @Test
        @DisplayName("검토 필요 테스트: OCR 신뢰도가 낮으면 needsReview가 true여야 한다")
        void shouldMarkNeedsReviewWhenConfidenceIsLow() {
            // given

            OcrResult lowConfidenceResult = OcrResult.builder()
                .fullText("차량번호 12가3456 계량일시 2026-02-05 10:00 10,000kg")
                .confidence(0.55)
                .build();// 신뢰도 0.6 미만

            // when
            WeightTicket result = parsingService.parse(lowConfidenceResult);

            // then
            assertThat(result.isNeedsReview()).isTrue();
        }

        @Test
        @DisplayName("검토 필요 테스트: 필수 데이터(차량번호) 추출 실패 시 needsReview가 true여야 한다")
        void shouldMarkNeedsReviewWhenDataIsMissing() {
            // given
            OcrResult missingDataResult = OcrResult.builder()
                .fullText("이 텍스트에는 차량번호 정보가 전혀 없습니다. 10,000kg")
                .confidence(0.99)
                .build();

            // when
            WeightTicket result = parsingService.parse(missingDataResult);

            // then
            assertThat(result.getCarNumber()).isEqualTo("UNKNOWN");
            assertThat(result.isNeedsReview()).isTrue();
        }

        @Test
        @DisplayName("검토 필요 테스트: 중량 정보가 하나도 없으면 needsReview가 true여야 한다")
        void shouldMarkNeedsReviewWhenWeightIsEmpty() {
            // given
            OcrResult noWeightResult = OcrResult.builder()
                .fullText("차량번호 12가3456 계량일시 2026-02-05 10:00 정보 없음")
                .confidence(0.95)
                .build();

            // when
            WeightTicket result = parsingService.parse(noWeightResult);

            // then
            assertThat(result.getGrossWeight()).isEqualTo(0.0);
            assertThat(result.isNeedsReview()).isTrue();
        }
    }
}
