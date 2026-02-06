package kr.co.reco.ocr.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import kr.co.reco.ocr.application.dto.OcrResult;
import kr.co.reco.ocr.domain.WeightTicket;
import kr.co.reco.ocr.domain.WeightTicketRepository;
import kr.co.reco.ocr.global.error.CustomException;
import kr.co.reco.ocr.global.error.ErrorCode;
import kr.co.reco.ocr.infrastructure.ocr.RawTextExtractor;
import kr.co.reco.ocr.infrastructure.ocr.RegexExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ParsingServiceTest {

    private RawTextExtractor extractor;
    private ParsingService parsingService;
    private RegexExtractor regexExtractor;

    @Mock
    private WeightTicketRepository weightTicketRepository;
    private final String SAMPLE_PATH = "src/main/resources/sample/";

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        extractor = new RawTextExtractor(objectMapper);

        this.regexExtractor = new RegexExtractor();
        parsingService = new ParsingServiceImpl(weightTicketRepository, regexExtractor);

        lenient().when(weightTicketRepository.save(any(WeightTicket.class)))
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
    @DisplayName("결측치 혹은 낮은 신뢰도로 인한 검토 필요")
    class MissingValueLogic {

        @Test
        @DisplayName("OCR 신뢰도가 0.6 미만이면 needsReview가 true이고 사유가 명시되어야 한다")
        void shouldMarkNeedsReviewWhenConfidenceIsLow() {
            OcrResult lowConfidenceResult = OcrResult.builder()
                .fullText("계량일시 2026-02-05 10:00 총중량 12,000kg 실중량 8,000kg")
                .confidence(0.55).build();

            WeightTicket result = parsingService.parse(lowConfidenceResult);

            assertThat(result.isNeedsReview()).isTrue();
            assertThat(result.getReviewNote()).contains("낮은 신뢰도");
        }

        @Test
        @DisplayName("차량번호 추출 실패 시 needsReview가 true이고 사유가 명시되어야 한다")
        void shouldMarkNeedsReviewWhenCarNumberIsMissing() {
            OcrResult missingCarResult = OcrResult.builder()
                .fullText("계량일시 2026-02-05 10:00 총중량 12,000kg 실중량 8,000kg") // 차량번호 패턴 없음
                .confidence(0.99).build();

            WeightTicket result = parsingService.parse(missingCarResult);

            assertThat(result.getCarNumber()).isEqualTo("UNKNOWN");
            assertThat(result.isNeedsReview()).isTrue();
            assertThat(result.getReviewNote()).contains("차량번호 누락");
        }
    }

    @Nested
    @DisplayName("데이터 이상치(Outlier) 탐지로 인한 검토 필요")
    class OutlierValueLogic {

        @Test
        @DisplayName("계량 시간이 미래 시간인 경우 needsReview가 true이고 사유가 명시되어야 한다")
        void shouldMarkNeedsReviewWhenDateIsFuture() {
            // given: 현재 시간(2026년)보다 훨씬 미래인 2099년 데이터
            OcrResult futureDateResult = OcrResult.builder()
                .fullText("차량번호 12가3456 계량일시 2099-12-31 23:59 10,000kg, 15,000kg")
                .confidence(0.99).build();

            // when
            WeightTicket result = parsingService.parse(futureDateResult);

            // then
            assertThat(result.isNeedsReview()).isTrue();
            assertThat(result.getReviewNote()).contains("미래 시간");
        }

        @Test
        @DisplayName("총중량이 실중량보다 작거나 같은 경우(중량 역전) needsReview가 true여야 한다")
        void shouldMarkNeedsReviewWhenWeightsAreInverted() {
            // given: 총중량(5,000) < 실중량(12,000)인 비정상 데이터
            OcrResult invertedWeightResult = OcrResult.builder()
                .fullText("차량번호 12가3456 계량일시 2026-02-05 10:00 총중량 5,000kg 실중량 12,000kg")
                .confidence(0.99).build();

            // when
            WeightTicket result = parsingService.parse(invertedWeightResult);
            System.out.println(result.getGrossWeight());
            System.out.println(result.getNetWeight());
            // then
            assertThat(result.isNeedsReview()).isTrue();
            assertThat(result.getReviewNote()).contains("중량 수치 이상");
        }
    }

    @Nested
    @DisplayName("실패 및 검토 시나리오 검증")
    class FailureAndReviewLogic {

        @Test
        @DisplayName("중량 데이터가 2개 미만(0개 또는 1개)이면 CustomException이 발생한다")
        void shouldThrowExceptionWhenWeightsAreInsufficient() {
            // given: 중량이 1개만 있는 데이터
            OcrResult insufficientWeightResult = OcrResult.builder()
                .fullText("차량번호 12가3456 계량일시 2026-02-05 10:00 총중량 10,000kg")
                .confidence(0.95).build();

            // when & then:
            assertThatThrownBy(() -> parsingService.parse(insufficientWeightResult))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OCR_PARSING_FAILED);
        }
    }
}
