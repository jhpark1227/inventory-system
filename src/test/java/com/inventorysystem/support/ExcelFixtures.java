package com.inventorysystem.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** 테스트에서 쓰는 엑셀 파일을 코드로 만든다. */
public final class ExcelFixtures {

    public static final Object[] HEADER = {"품목ID", "품목명", "현재 수량"};
    public static final Object[] HEADER_WITH_DATE = {"품목ID", "품목명", "현재 수량", "최종 수정일시"};

    private ExcelFixtures() {
    }

    /** 수식 셀을 만들기 위한 표시용 값. */
    public record Formula(String expression) {
    }

    /** {@code 재고} 시트 하나로 된 파일을 만든다. 첫 행이 헤더이다. 값은 String, Number, LocalDateTime, Formula, null을 쓸 수 있다. */
    public static byte[] stock(Object[]... rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fill(workbook, workbook.createSheet("재고"), rows);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void fill(XSSFWorkbook workbook, Sheet sheet, Object[]... rows) {
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-MM-dd HH:mm:ss"));
        for (int r = 0; r < rows.length; r++) {
            Row row = sheet.createRow(r);
            for (int c = 0; c < rows[r].length; c++) {
                Object value = rows[r][c];
                if (value == null) {
                    continue;
                }
                Cell cell = row.createCell(c);
                switch (value) {
                    case String s -> cell.setCellValue(s);
                    case Number n -> cell.setCellValue(n.doubleValue());
                    case LocalDateTime t -> {
                        cell.setCellValue(t);
                        cell.setCellStyle(dateStyle);
                    }
                    case Formula f -> cell.setCellFormula(f.expression());
                    default -> throw new IllegalArgumentException("지원하지 않는 값: " + value);
                }
            }
        }
    }

    /** 예시 데이터와 같은 내용의 파일 (D열 없음). */
    public static byte[] sample() {
        return stock(HEADER,
                new Object[] {1, "무선마우스", 30},
                new Object[] {2, "키보드", 20},
                new Object[] {3, "USB 메모리", 50});
    }
}
