package kr.co.reco.ocr.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.co.reco.ocr.application.dto.OcrResult;
import kr.co.reco.ocr.domain.WeightTicket;
import kr.co.reco.ocr.domain.WeightTicketRepository;
import kr.co.reco.ocr.global.error.CustomException;
import kr.co.reco.ocr.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParsingServiceImpl implements ParsingService {

    private final WeightTicketRepository weightTicketRepository;

    private static final Pattern CAR_PATTERN =
        Pattern.compile("(차\\s*량\\s*번\\s*호|차\\s*번\\s*호|차\\s*량\\s*No\\.?)[^0-9가-힣]*([0-9가-힣\\s]{4,15})");
    private static final Pattern CAR_NUMBER_FORMAT_PATTERN =
        Pattern.compile("(\\d{2,3}[가-힣]\\d{4}|\\d{4})");

    private static final Pattern KG_PATTERN =
        Pattern.compile("([^kg\\n]{4,20})\\s*kg", Pattern.CASE_INSENSITIVE);
    private static final String TIME_REGEX = "\\d{2}:\\d{2}(?::\\d{2})?";

    private static final String DATE_REGEX = "(\\d{4}|\\d{2})[-./]\\d{2}[-./]\\d{2}";
    @Override
    public WeightTicket parse(OcrResult ocrResult) {

        if (ocrResult == null || ocrResult.getFullText() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        String text = ocrResult.getFullText();

        List<Double> weights = extractWeightsByReverse(text);

        if (weights.isEmpty()) {
            throw new CustomException(ErrorCode.OCR_PARSING_FAILED);
        }

        String carNumber = extractCarNumber(text);
        LocalDateTime scaledAt = extractScaledAt(text);
        WeightValues weightValues = resolveWeightValues(weights);
        WeightTicket ticket = WeightTicket.create(
            carNumber,
            weightValues.grossWeight(),
            weightValues.tareWeight(),
            weightValues.netWeight(),
            scaledAt,
            ocrResult.getConfidence()
        );

        return weightTicketRepository.save(ticket);
    }

    private WeightValues resolveWeightValues(List<Double> weights) {
        double gross = 0.0;
        double tare = 0.0;
        double net = 0.0;

        if (weights.size() >= 3) {
            gross = weights.get(0);
            tare = weights.get(1);
            net = weights.get(2);
        } else if (weights.size() == 2) {
            gross = weights.get(0);
            net = weights.get(1);
            tare = gross - net;
        }
        return new WeightValues(gross, tare, net);
    }

    private record WeightValues(double grossWeight, double tareWeight, double netWeight) {}

    private String extractCarNumber(String text) {
        Matcher carLabelMatcher = CAR_PATTERN.matcher(text);
        if (carLabelMatcher.find()) {
            String carNumberCandidate = carLabelMatcher.group(2).replaceAll("\\s", "");
            Matcher carNumberFormatMatcher = CAR_NUMBER_FORMAT_PATTERN.matcher(carNumberCandidate);
            if (carNumberFormatMatcher.find()) return carNumberFormatMatcher.group(1);
        }
        return "UNKNOWN";
    }

    private List<Double> extractWeightsByReverse(String text) {
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

    private String getOnlyNumber(Matcher kgMatcher) {
        String segment = kgMatcher.group(1); // 예: "05:26:18 12,480"
        String cleanSegment = segment;

        if (segment.contains("분")) {
            cleanSegment = segment.replaceAll(".*분\\s*", "");
        }

        else if (segment.contains(":")) {
            cleanSegment = segment.replaceAll("\\d{1,2}\\s*:\\s*\\d{1,2}(\\s*:\\s*\\d{1,2})?", "");
            cleanSegment = cleanSegment.replaceAll(".*[:시]\\s*", "");
        }

        return cleanSegment.replaceAll("[^0-9,.]", "").replaceAll("[,\\s]", "");
    }

    private LocalDateTime extractScaledAt(String text) {
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

    private String findLastPattern(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        String lastMatch = null;

        while (matcher.find()) {
            lastMatch = matcher.group();
        }

        return lastMatch;
    }
}
