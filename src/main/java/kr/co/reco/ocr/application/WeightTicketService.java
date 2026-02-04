package kr.co.reco.ocr.application;

import java.util.List;
import kr.co.reco.ocr.application.dto.OcrResult;
import kr.co.reco.ocr.application.dto.WeightTicketSearchRequest;
import kr.co.reco.ocr.domain.WeightTicket;
import kr.co.reco.ocr.domain.WeightTicketQueryRepository;
import kr.co.reco.ocr.infrastructure.exporter.FileExporter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WeightTicketService {

    private final WeightTicketQueryRepository queryRepository;
    private final ParsingService parsingService;
    private final FileExporter fileExporter;

    @Transactional
    public WeightTicket parseAndSave(OcrResult ocrResult) {
        WeightTicket savedTicket = parsingService.parse(ocrResult);
        fileExporter.export(savedTicket);
        return savedTicket;
    }

    public List<WeightTicket> searchTickets(WeightTicketSearchRequest condition) {
        return queryRepository.search(condition);
    }

}
