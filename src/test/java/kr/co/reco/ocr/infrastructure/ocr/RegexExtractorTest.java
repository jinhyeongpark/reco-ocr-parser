package kr.co.reco.ocr.infrastructure.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RegexExtractorTest {

    private final RegexExtractor regexExtractor = new RegexExtractor();

    @Nested
    @DisplayName("중량 추출 검증")
    class WeightExtraction {
        @ParameterizedTest
        @CsvSource({
            "'총중량 13 460 kg', 13460.0",
            "'2026-02-05 5,000kg', 5000.0",
            "'05:36:01 10시 7,470kg', 7470.0",
            "'실중량 12000kg', 12000.0",
            "'2026.10.10 8,150 kg', 8150.0"
        })
        @DisplayName("다양한 노이즈가 섞인 텍스트에서 정확한 중량 숫자만 추출해야 한다")
        void shouldExtractCorrectWeights(String input, Double expectedWeight) {
            List<Double> result = regexExtractor.extractWeights(input);
            assertThat(result).contains(expectedWeight);
        }
    }

    @Nested
    @DisplayName("차량 번호 추출 검증")
    class CarNumberExtraction {
        @ParameterizedTest
        @CsvSource({
            "'차량번호 12가3456', '12가3456'",
            "'차 량 번 호 80구8713', '80구8713'",
            "'차량No. 0580', '0580'",
            "'번호 없음', 'UNKNOWN'"
        })
        @DisplayName("차량 라벨과 형식에 맞는 번호를 정확히 추출해야 한다")
        void shouldExtractCorrectCarNumber(String input, String expected) {
            String result = regexExtractor.extractCarNumber(input);
            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("계량 일시 추출 검증")
    class ScaledAtExtraction {
        @Test
        @DisplayName("날짜와 시간이 모두 포함된 경우 LocalDateTime으로 변환한다")
        void shouldExtractDateTime() {
            String text = "계량일시 2026-02-05 10:30:45";
            LocalDateTime result = regexExtractor.extractScaledAt(text);

            assertThat(result).isEqualTo(LocalDateTime.of(2026, 2, 5, 10, 30, 45));
        }

        @ParameterizedTest
        @CsvSource({
            "'계량일 2026.02.02', 2026, 2, 2",
            "'날짜: 2025/12/01', 2025, 12, 1"
        })
        @DisplayName("다양한 구분자의 날짜만 있는 경우 해당 날짜의 00:00를 반환한다")
        void shouldExtractDateOnly(String input, int year, int month, int day) {
            LocalDateTime result = regexExtractor.extractScaledAt(input);

            assertThat(result.toLocalDate()).isEqualTo(LocalDateTime.of(year, month, day, 0, 0).toLocalDate());
        }
    }
}
