package kr.co.reco.ocr.infrastructure.exporter;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import kr.co.reco.ocr.domain.WeightTicket;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
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
            throw new RuntimeException("JSON 저장 실패: " + ticket.getId(), e);
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
            throw new RuntimeException("CSV 저장 실패", e);
        }
    }

    private void ensureDirectoryExists() {
        File dir = new File(outputPath);
        if (!dir.exists()) dir.mkdirs();
    }

}
