package kr.co.reco.ocr.presentation;

import jakarta.validation.Valid;
import java.util.List;
import kr.co.reco.ocr.application.WeightTicketService;
import kr.co.reco.ocr.application.dto.OcrResult;
import kr.co.reco.ocr.application.dto.WeightTicketSearchRequest;
import kr.co.reco.ocr.application.dto.WeightTicketUpdateRequest;
import kr.co.reco.ocr.domain.WeightTicket;
import kr.co.reco.ocr.global.common.ApiResponse;
import kr.co.reco.ocr.infrastructure.ocr.RawTextExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/weight-tickets")
@RequiredArgsConstructor
public class WeightTicketController {

    private final WeightTicketService weightTicketService;
    private final RawTextExtractor extractor;

    @PostMapping("/samples/{fileName}")
    public ResponseEntity<ApiResponse<WeightTicket>> parseSample(@PathVariable("fileName") String fileName) {
        OcrResult ocrResult = extractor.extract("src/main/resources/sample/" + fileName);
        return ResponseEntity.ok(ApiResponse.onSuccess(weightTicketService.parseAndSave(ocrResult)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<WeightTicket>> updateTicket(
        @PathVariable("id") Long id,
        @RequestBody @Valid WeightTicketUpdateRequest request) {

        WeightTicket updated = weightTicketService.update(id, request);
        return ResponseEntity.ok(ApiResponse.onSuccess(updated));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<WeightTicket>>> getTickets(
        WeightTicketSearchRequest condition) {
        return ResponseEntity.ok(ApiResponse.onSuccess(weightTicketService.searchTickets(condition)));
    }
}
