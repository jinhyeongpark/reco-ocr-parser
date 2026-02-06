package kr.co.reco.ocr.application;

import java.time.LocalDateTime;
import java.util.List;
import kr.co.reco.ocr.application.dto.OcrResult;
import kr.co.reco.ocr.domain.WeightTicket;
import kr.co.reco.ocr.domain.WeightTicketRepository;
import kr.co.reco.ocr.global.error.CustomException;
import kr.co.reco.ocr.global.error.ErrorCode;
import kr.co.reco.ocr.infrastructure.ocr.RegexExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParsingServiceImpl implements ParsingService {

    private final WeightTicketRepository weightTicketRepository;
    private final RegexExtractor regexExtractor;

    @Override
    public WeightTicket parse(OcrResult ocrResult) {

        if (ocrResult == null || ocrResult.getFullText() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        String text = ocrResult.getFullText();

        List<Double> weights = regexExtractor.extractWeights(text);
        if (weights.size() < 2) {
            throw new CustomException(ErrorCode.OCR_PARSING_FAILED);
        }

        String carNumber = regexExtractor.extractCarNumber(text);
        LocalDateTime scaledAt = regexExtractor.extractScaledAt(text);
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
}
