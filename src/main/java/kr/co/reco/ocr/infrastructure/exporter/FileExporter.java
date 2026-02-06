package kr.co.reco.ocr.infrastructure.exporter;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import kr.co.reco.ocr.domain.WeightTicket;
import kr.co.reco.ocr.global.error.CustomException;
import kr.co.reco.ocr.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileExporter {

    private final ObjectMapper objectMapper; // JSON용

    @Value("${app.ocr.output-path}")
    private String outputPath;

    public void export(WeightTicket ticket) {
        ensureDirectoryExists();
        saveToJson(ticket);
        saveToCsv(ticket);
    }

    private void saveToJson(WeightTicket ticket) {
        File file = new File(outputPath, "ticket_" + ticket.getId() + ".json");
        try {
            objectMapper.writeValue(file, ticket);
        } catch (IOException e) {
            log.error("JSON Export Failed: Ticket ID {}", ticket.getId(), e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void saveToCsv(WeightTicket ticket) {
        File file = new File(outputPath, "ticket_" + ticket.getId() + ".csv");
        try (PrintWriter writer = new PrintWriter(file)) {
            writer.println("ID,CarNumber,Gross,Tare,Net,ScaledAt");
            writer.printf("%d,%s,%.1f,%.1f,%.1f,%s%n",
                ticket.getId(), ticket.getCarNumber(), ticket.getGrossWeight(),
                ticket.getTareWeight(), ticket.getNetWeight(), ticket.getScaledAt());
        } catch (IOException e) {
            log.error("Csv Export Failed: Ticket ID {}", ticket.getId(), e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void ensureDirectoryExists() {
        File dir = new File(outputPath);
        if (!dir.exists()) dir.mkdirs();
    }

}
