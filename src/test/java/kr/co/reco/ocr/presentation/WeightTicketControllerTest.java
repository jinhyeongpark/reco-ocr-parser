package kr.co.reco.ocr.presentation;

// JUnit 5 & AssertJ
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

// Spring MockMvc (Static Imports)
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
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
            .andExpect(jsonPath("$.carNumber").value("8713"))
            .andExpect(jsonPath("$.grossWeight").value(12480.0))
            .andExpect(jsonPath("$.needsReview").value(false))
            .andExpect(jsonPath("$.reviewNote").isEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

        Integer actualId = com.jayway.jsonpath.JsonPath.read(contentAsString, "$.id");

        Path jsonPath = Paths.get("output", "ticket_" + actualId + ".json");
        Path csvPath = Paths.get("output", "ticket_" + actualId + ".csv");

        assertThat(Files.exists(jsonPath)).isTrue();
        assertThat(Files.exists(csvPath)).isTrue();
    }

    @Test
    @DisplayName("통합 시나리오 2: QueryDSL 필터링을 통해 특정 차량번호를 검색한다")
    void searchTicketsWithFilters() throws Exception {
        // given: 먼저 데이터 하나를 파싱해서 넣어둠 (POST 호출)
        mockMvc.perform(post("/api/v1/weight-tickets/samples/sample_02.json"));

        // when & then: 차량번호 '80'이 포함된 티켓 검색 (샘플2: 80구8713)
        mockMvc.perform(get("/api/v1/weight-tickets")
                .param("carNumber", "80")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$[0].carNumber", containsString("80")));
    }
}
