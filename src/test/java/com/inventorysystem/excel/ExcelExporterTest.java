package com.inventorysystem.excel;

import static com.inventorysystem.support.ExcelFixtures.fill;
import static org.assertj.core.api.Assertions.assertThat;

import com.inventorysystem.domain.Item;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** UT-08, UT-09: 엑셀 내보내기 */
class ExcelExporterTest {

    private final ExcelItemParser parser = new ExcelItemParser();
    private final ExcelExporter exporter = new ExcelExporter();

    /** C열 뒤에 사용자 열(E열 '비고')과 다른 시트('메모')가 있는 원본 파일. D열은 없다. */
    private byte[] original() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fill(workbook, workbook.createSheet("재고"),
                    new Object[] {"품목ID", "품목명", "현재 수량", null, "비고"},
                    new Object[] {1, "무선마우스", 30, null, "A동"},
                    new Object[] {2, "키보드", 20, null, "B동"});
            fill(workbook, workbook.createSheet("메모"), new Object[] {"담당자 메모"});
            workbook.write(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("UT-08 내보낸 파일에 저장본 수량과 수정일시가 반영되고 나머지는 유지된다")
    void exportKeepsEverythingElse() throws Exception {
        byte[] original = original();
        List<Item> items = parser.parse(original);
        LocalDateTime savedAt = LocalDateTime.of(2026, 11, 11, 15, 20, 41);
        items.get(1).setQuantity(25);
        items.get(1).commit(savedAt);

        byte[] exported = exporter.export(original, items);

        List<Item> reread = parser.parse(exported);
        assertThat(reread).extracting(Item::getQuantity).containsExactly(30L, 25L);
        assertThat(reread.get(1).getUpdatedAt()).isEqualTo(savedAt);
        assertThat(reread.get(0).getUpdatedAt()).isNull();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(exported))) {
            Sheet stock = workbook.getSheet("재고");
            assertThat(stock.getRow(0).getCell(3).getStringCellValue()).isEqualTo("최종 수정일시");
            assertThat(stock.getRow(1).getCell(4).getStringCellValue()).isEqualTo("A동");
            assertThat(stock.getRow(2).getCell(4).getStringCellValue()).isEqualTo("B동");
            assertThat(workbook.getSheet("메모").getRow(0).getCell(0).getStringCellValue()).isEqualTo("담당자 메모");
        }
    }

    @Test
    @DisplayName("UT-09 저장하지 않은 작업본 수량은 파일에 들어가지 않는다")
    void exportExcludesUnsavedDraft() throws Exception {
        byte[] original = original();
        List<Item> items = parser.parse(original);
        items.get(0).setQuantity(99);

        byte[] exported = exporter.export(original, items);

        assertThat(parser.parse(exported)).extracting(Item::getQuantity).containsExactly(30L, 20L);
    }
}
