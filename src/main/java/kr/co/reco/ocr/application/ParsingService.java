package kr.co.reco.ocr.application;

import kr.co.reco.ocr.application.dto.OcrResult;
import kr.co.reco.ocr.domain.WeightTicket;

public interface ParsingService {
    WeightTicket parse(OcrResult ocrResult);
}
