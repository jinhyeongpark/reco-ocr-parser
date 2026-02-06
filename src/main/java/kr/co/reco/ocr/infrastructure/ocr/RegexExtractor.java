package kr.co.reco.ocr.infrastructure.ocr;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RegexExtractor {

    private static final Pattern CAR_PATTERN =
        Pattern.compile("(차\\s*량\\s*번\\s*호|차\\s*번\\s*호|차\\s*량\\s*No\\.?)[^0-9가-힣]*([0-9가-힣\\s]{4,15})");
    private static final Pattern CAR_NUMBER_FORMAT_PATTERN =
        Pattern.compile("(\\d{2,3}[가-힣]\\d{4}|\\d{4})");

    private static final Pattern KG_PATTERN =
        Pattern.compile("([^kg\\n]{4,20})\\s*kg", Pattern.CASE_INSENSITIVE);
    private static final String TIME_REGEX = "\\d{2}:\\d{2}(?::\\d{2})?";
    private static final String DATE_REGEX = "(\\d{4}|\\d{2})[-./]\\d{2}[-./]\\d{2}";

    public String extractCarNumber(String text) {
        Matcher carLabelMatcher = CAR_PATTERN.matcher(text);
        if (carLabelMatcher.find()) {
            String carNumberCandidate = carLabelMatcher.group(2).replaceAll("\\s", "");
            Matcher carNumberFormatMatcher = CAR_NUMBER_FORMAT_PATTERN.matcher(carNumberCandidate);
            if (carNumberFormatMatcher.find()) return carNumberFormatMatcher.group(1);
        }
        return "UNKNOWN";
    }

    public List<Double> extractWeights(String text) {
        List<Double> extractedWeights = new ArrayList<>();
        Matcher kgMatcher = KG_PATTERN.matcher(text);

        while (kgMatcher.find()) {
            String onlyNumber = getOnlyNumber(kgMatcher);
            if (!onlyNumber.isEmpty()) {
                try {
                    extractedWeights.add(Double.parseDouble(onlyNumber));
                } catch (NumberFormatException e) {
                    log.warn("중량 변환 실패: {}", onlyNumber);
                }
            }
        }
        return extractedWeights;
    }

    public LocalDateTime extractScaledAt(String text) {
        String lastDate = findLastPattern(text, DATE_REGEX);
        String lastTime = findLastPattern(text, TIME_REGEX);

        if (lastDate == null) return null;

        String normalizedDate = lastDate.replace(".", "-").replace("/", "-");

        try {
            if (lastTime != null) {
                String dateTimeStr = normalizedDate + " " + lastTime;
                String format = lastTime.length() > 5 ? "yyyy-MM-dd HH:mm:ss" : "yyyy-MM-dd HH:mm";
                return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern(format));
            } else {
                return LocalDate.parse(normalizedDate).atStartOfDay();
            }
        } catch (Exception e) {
            log.warn("날짜/시간 조립 실패: date={}, time={}", lastDate, lastTime);
            return null;
        }
    }

    private String getOnlyNumber(Matcher kgMatcher) {
        String rawSegment = kgMatcher.group(1);
        String preProcessed = removeTimeReference(rawSegment);

        String weightRegion = preProcessed.replaceAll("^.*[^0-9,.\\s]+", "");

        return weightRegion.replaceAll("[^0-9.]", "");
    }

    private String removeTimeReference(String segment) {
        segment = segment.replaceAll("\\d{2,4}[-./]\\d{1,2}[-./]\\d{1,2}", "");

        if (segment.contains("분")) {
            return segment.replaceAll(".*분\\s*", "");
        }
        if (segment.contains(":")) {
            String noFormatTime = segment.replaceAll("\\d{1,2}\\s*:\\s*\\d{1,2}(\\s*:\\s*\\d{1,2})?", "");
            return noFormatTime.replaceAll(".*[:시]\\s*", "");
        }
        return segment;
    }

    private String findLastPattern(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        String lastMatch = null;
        while (matcher.find()) {
            lastMatch = matcher.group();
        }
        return lastMatch;
    }
}
