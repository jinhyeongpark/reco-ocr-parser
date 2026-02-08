package kr.co.reco.ocr.application;

import java.util.List;
import kr.co.reco.ocr.application.dto.OcrResult;
import kr.co.reco.ocr.application.dto.WeightTicketSearchRequest;
import kr.co.reco.ocr.application.dto.WeightTicketUpdateRequest;
import kr.co.reco.ocr.domain.WeightTicket;
import kr.co.reco.ocr.domain.WeightTicketQueryRepository;
import kr.co.reco.ocr.domain.WeightTicketRepository;
import kr.co.reco.ocr.global.error.CustomException;
import kr.co.reco.ocr.global.error.ErrorCode;
import kr.co.reco.ocr.infrastructure.exporter.FileExporter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WeightTicketService {

    private final WeightTicketRepository weightTicketRepository;
    private final WeightTicketQueryRepository queryRepository;
    private final ParsingService parsingService;
    private final FileExporter fileExporter;

    @Transactional
    public WeightTicket parseAndSave(OcrResult ocrResult) {
        WeightTicket savedTicket = parsingService.parse(ocrResult);
        fileExporter.export(savedTicket);
        return savedTicket;
    }

    @Transactional
    public WeightTicket update(Long id, WeightTicketUpdateRequest dto) {
        WeightTicket ticket = weightTicketRepository.findById(id)
            .orElseThrow(() -> new CustomException(ErrorCode.TICKET_NOT_FOUND));

        ticket.update(
            dto.getCarNumber(),
            dto.getGrossWeight(),
            dto.getTareWeight(),
            dto.getNetWeight(),
            dto.getScaledAt());

        fileExporter.export(ticket);

        return ticket;
    }

    public List<WeightTicket> searchTickets(WeightTicketSearchRequest condition) {
        return queryRepository.search(condition);
    }

}
