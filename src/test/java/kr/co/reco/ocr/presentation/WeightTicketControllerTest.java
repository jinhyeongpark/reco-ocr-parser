package kr.co.reco.ocr.presentation;

// JUnit 5 & AssertJ
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import kr.co.reco.ocr.application.dto.WeightTicketUpdateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

// Spring MockMvc (Static Imports)
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

// Spring Test Annotations
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;


@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WeightTicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanUp() throws Exception {
        Path outputDir = Paths.get("output");
        if (Files.exists(outputDir)) {
            try (Stream<Path> pathStream = Files.walk(outputDir)) {
                pathStream
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            System.err.println("파일 삭제 실패: " + path);
                        }
                    });
            }
        }
    }

    @Test
    @DisplayName("통합 시나리오 1: 샘플 JSON 파일을 파싱하여 DB에 저장하고 결과를 반환한다")
    void parseAndSaveSampleFile() throws Exception {
        // given: 프로젝트 내에 존재하는 sample_01.json 대상
        String fileName = "sample_01.json";

        // when & then: POST 요청을 보내고 그 결과를 문자열로 받아 id 추출
        String contentAsString = mockMvc.perform(post("/api/v1/weight-tickets/samples/" + fileName))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.carNumber").value("8713"))
            .andExpect(jsonPath("$.result.grossWeight").value(12480.0))
            .andExpect(jsonPath("$.result.needsReview").value(false))
            .andExpect(jsonPath("$.result.reviewNote").isEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

        Integer actualId = com.jayway.jsonpath.JsonPath.read(contentAsString, "$.result.id");

        Path jsonPath = Paths.get("output", "ticket_" + actualId + ".json");
        Path csvPath = Paths.get("output", "ticket_" + actualId + ".csv");

        assertThat(Files.exists(jsonPath)).isTrue();
        assertThat(Files.exists(csvPath)).isTrue();
    }

    @Test
    @DisplayName("통합 시나리오 3: 계근 데이터를 수정하면 DB와 물리 파일이 모두 갱신되어야 한다")
    void updateTicketAndSyncFiles() throws Exception {
        // given
        String initialContent = mockMvc.perform(post("/api/v1/weight-tickets/samples/sample_01.json"))
            .andReturn().getResponse().getContentAsString();

        Integer targetId = com.jayway.jsonpath.JsonPath.read(initialContent, "$.result.id");

        // when
        WeightTicketUpdateRequest updateRequest = new WeightTicketUpdateRequest(
            "99가9999",      // 변경된 차량번호
            15000.0,        // 변경된 총중량
            7000.0,         // 변경된 공차중량
            8000.0,         // 변경된 실중량
            LocalDateTime.of(2026, 2, 8, 15, 0) // 변경된 시간
        );

        mockMvc.perform(patch("/api/v1/weight-tickets/" + targetId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.carNumber").value("99가9999"))
            .andExpect(jsonPath("$.result.needsReview").value(false))
            .andExpect(jsonPath("$.result.reviewNote").value(containsString("수기 수정 완료")));

        // then
        Path jsonPath = Paths.get("output", "ticket_" + targetId + ".json");
        String updatedFileContent = Files.readString(jsonPath);

        assertThat(updatedFileContent).contains("99가9999");
        assertThat(updatedFileContent).contains("15000.0");

        Path csvPath = Paths.get("output", "ticket_" + targetId + ".csv");
        List<String> csvLines = Files.readAllLines(csvPath);
        assertThat(csvLines.get(1)).contains("99가9999");
    }

    @Test
    @DisplayName("통합 시나리오 3: QueryDSL 필터링을 통해 특정 차량번호를 검색한다")
    void searchTicketsWithFilters() throws Exception {
        // given: 먼저 데이터 하나를 파싱해서 넣어둠 (POST 호출)
        mockMvc.perform(post("/api/v1/weight-tickets/samples/sample_02.json"));

        // when & then: 차량번호 '80'이 포함된 티켓 검색 (샘플2: 80구8713)
        mockMvc.perform(get("/api/v1/weight-tickets")
                .param("carNumber", "80")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$.result[0].carNumber", containsString("80")));
    }
}
