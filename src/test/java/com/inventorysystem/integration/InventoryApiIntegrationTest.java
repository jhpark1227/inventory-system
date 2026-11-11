package com.inventorysystem.integration;

import static com.inventorysystem.support.ExcelFixtures.HEADER;
import static com.inventorysystem.support.ExcelFixtures.stock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventorysystem.domain.Item;
import com.inventorysystem.excel.ExcelItemParser;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** IT-01 ~ IT-05: 스프링 컨텍스트를 띄워 API 전체 흐름을 검증한다. 한 시나리오 안에서는 같은 세션을 쓴다. */
@SpringBootTest
@AutoConfigureMockMvc
class InventoryApiIntegrationTest {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String QUANTITY_5 = "{\"quantity\": 5}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExcelItemParser parser;

    @Test
    @DisplayName("IT-01 처음 접속하면 예시 데이터 3건이 등록순으로 조회된다")
    void initialList() throws Exception {
        mockMvc.perform(get("/api/items").session(new MockHttpSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("inventory.xlsx"))
                .andExpect(jsonPath("$.dirty").value(false))
                .andExpect(jsonPath("$.unexported").value(false))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].name").value("무선마우스"))
                .andExpect(jsonPath("$.items[0].quantity").value(30))
                .andExpect(jsonPath("$.items[1].name").value("키보드"))
                .andExpect(jsonPath("$.items[1].quantity").value(20))
                .andExpect(jsonPath("$.items[2].name").value("USB 메모리"))
                .andExpect(jsonPath("$.items[2].quantity").value(50))
                .andExpect(jsonPath("$.items[0].updatedAt").exists());
    }

    @Test
    @DisplayName("IT-02 입고, 출고, 저장, 되돌리기 흐름")
    void changeSaveAndDiscard() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/items/2/inbound").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(QUANTITY_5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.quantity").value(25))
                .andExpect(jsonPath("$.item.savedQuantity").value(20))
                .andExpect(jsonPath("$.item.changed").value(true))
                .andExpect(jsonPath("$.dirty").value(true))
                .andExpect(jsonPath("$.changedCount").value(1));
        mockMvc.perform(post("/api/items/3/outbound").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\": 10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changedCount").value(2));
        mockMvc.perform(get("/api/items").session(session))
                .andExpect(jsonPath("$.dirty").value(true))
                .andExpect(jsonPath("$.unexported").value(false));

        mockMvc.perform(post("/api/items/save").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedCount").value(2))
                .andExpect(jsonPath("$.dirty").value(false))
                .andExpect(jsonPath("$.unexported").value(true))
                .andExpect(jsonPath("$.items[1].savedQuantity").value(25))
                .andExpect(jsonPath("$.items[2].savedQuantity").value(40))
                .andExpect(jsonPath("$.items[1].updatedAt").exists());

        mockMvc.perform(post("/api/items/1/inbound").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(QUANTITY_5))
                .andExpect(jsonPath("$.item.quantity").value(35));
        mockMvc.perform(post("/api/items/discard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dirty").value(false))
                .andExpect(jsonPath("$.unexported").value(true))
                .andExpect(jsonPath("$.items[0].quantity").value(30))
                .andExpect(jsonPath("$.items[1].quantity").value(25));
    }

    @Test
    @DisplayName("IT-03 오류 응답은 400과 message만 담고 데이터는 바뀌지 않는다")
    void errorResponses() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/items/2/outbound").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\": 100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("재고가 부족합니다. 현재 재고는 20개입니다."))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(post("/api/items/99/inbound").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(QUANTITY_5))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
        for (String body : List.of("{\"quantity\": 5.5}", "{\"quantity\": \"5\"}", "{}", "{\"quantity\": -1}",
                "{\"quantity\": 1000000000000000}", "not json")) {
            mockMvc.perform(post("/api/items/2/inbound").session(session)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        mockMvc.perform(get("/api/items").session(session))
                .andExpect(jsonPath("$.dirty").value(false))
                .andExpect(jsonPath("$.items[1].quantity").value(20));
    }

    @Test
    @DisplayName("IT-04 첨부, 수정, 저장, 다운로드: 저장한 변경만 파일에 담긴다")
    void uploadEditSaveDownload() throws Exception {
        MockHttpSession session = new MockHttpSession();
        byte[] original = stock(HEADER, new Object[] {10, "A4용지", 10}, new Object[] {20, "볼펜", 5});

        mockMvc.perform(multipart("/api/items/upload")
                        .file(new MockMultipartFile("file", "stock.xlsx", XLSX, original)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount").value(2))
                .andExpect(jsonPath("$.fileName").value("stock.xlsx"))
                .andExpect(jsonPath("$.items[0].name").value("A4용지"))
                .andExpect(jsonPath("$.items[0].updatedAt").doesNotExist());
        mockMvc.perform(get("/api/items").session(session))
                .andExpect(jsonPath("$.fileName").value("stock.xlsx"))
                .andExpect(jsonPath("$.items.length()").value(2));

        mockMvc.perform(post("/api/items/10/inbound").session(session)
                .contentType(MediaType.APPLICATION_JSON).content(QUANTITY_5)).andExpect(status().isOk());
        mockMvc.perform(post("/api/items/save").session(session)).andExpect(status().isOk());
        mockMvc.perform(post("/api/items/20/outbound").session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\": 2}")).andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/items/download").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(XLSX))
                .andReturn();

        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertThat(disposition).startsWith("attachment").contains("filename*=UTF-8''");
        String fileName = URLDecoder.decode(disposition.substring(disposition.indexOf("''") + 2),
                StandardCharsets.UTF_8);
        assertThat(fileName).matches("stock_수정_\\d{8}_\\d{6}\\.xlsx");

        List<Item> downloaded = parser.parse(result.getResponse().getContentAsByteArray());
        assertThat(downloaded).extracting(Item::getQuantity).containsExactly(15L, 5L);
        assertThat(downloaded.get(0).getUpdatedAt()).isNotNull();
        assertThat(downloaded.get(1).getUpdatedAt()).isNull();

        mockMvc.perform(get("/api/items").session(session))
                .andExpect(jsonPath("$.unexported").value(false))
                .andExpect(jsonPath("$.dirty").value(true))
                .andExpect(jsonPath("$.items[1].quantity").value(3));
    }

    @Test
    @DisplayName("IT-05 잘못된 엑셀을 첨부하면 400이고 기존 데이터가 그대로 유지된다")
    void invalidUploadKeepsExistingData() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/items/2/inbound").session(session)
                .contentType(MediaType.APPLICATION_JSON).content(QUANTITY_5)).andExpect(status().isOk());
        byte[] invalid = stock(HEADER, new Object[] {1, "정상", 1}, new Object[] {2, "오류", -3});

        mockMvc.perform(multipart("/api/items/upload")
                        .file(new MockMultipartFile("file", "bad.xlsx", XLSX, invalid)).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("3행")))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(multipart("/api/items/upload")
                        .file(new MockMultipartFile("file", "memo.txt", "text/plain", "x".getBytes()))
                        .session(session))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/items").session(session))
                .andExpect(jsonPath("$.fileName").value("inventory.xlsx"))
                .andExpect(jsonPath("$.dirty").value(true))
                .andExpect(jsonPath("$.items[1].quantity").value(25));
    }
}
