package com.inventorysystem.excel;

import static com.inventorysystem.excel.ExcelLayout.*;

import com.inventorysystem.domain.Item;
import com.inventorysystem.domain.Quantity;
import com.inventorysystem.exception.InvalidRequestException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * 엑셀 파일(바이트)을 읽어 형식 규칙을 검증하고 품목 목록으로 변환한다.
 * 오류가 하나라도 있으면 행별 이유(최대 20건)를 메시지에 담아 {@link InvalidRequestException}을 던진다.
 */
@Component
public class ExcelItemParser {

    static final int MAX_ROWS = 1000;
    static final int MAX_ERRORS = 20;

    private static final String OPEN_FAILED =
            "엑셀 파일을 열 수 없습니다. .xlsx 형식의 파일인지 확인해 주세요.";
    private static final String NOT_VALID = "엑셀 파일의 내용이 올바르지 않아 불러오지 못했습니다.";

    public List<Item> parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new InvalidRequestException("엑셀 파일이 비어 있습니다.");
        }
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = findSheet(workbook);
            if (sheet == null) {
                throw new InvalidRequestException("엑셀 파일에 시트가 없습니다.");
            }
            return parseSheet(sheet);
        } catch (IOException | RuntimeException e) {
            if (e instanceof InvalidRequestException invalid) {
                throw invalid;
            }
            throw new InvalidRequestException(OPEN_FAILED);
        }
    }

    private List<Item> parseSheet(Sheet sheet) {
        Row header = sheet.getRow(0);
        List<String> errors = new ArrayList<>();
        if (!headerMatches(header)) {
            throw invalid(List.of("1행: A~C열 헤더는 '품목ID', '품목명', '현재 수량'이어야 합니다."));
        }
        boolean hasUpdatedAt = HEADER_UPDATED_AT.equals(headerText(header.getCell(COL_UPDATED_AT)));

        List<Item> items = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        int dataRows = 0;
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (isEmptyRow(row, hasUpdatedAt)) {
                continue;
            }
            if (++dataRows > MAX_ROWS) {
                throw invalid(List.of("데이터 행은 최대 " + MAX_ROWS + "행까지 허용합니다."));
            }
            int line = r + 1;
            int before = errors.size();

            Long id = readId(row.getCell(COL_ID), line, errors);
            if (id != null && !ids.add(id)) {
                addError(errors, line + "행: 품목ID " + id + "가 중복되었습니다.");
            }
            String name = readName(row.getCell(COL_NAME), line, errors);
            Long quantity = readQuantity(row.getCell(COL_QUANTITY), line, errors);
            LocalDateTime updatedAt = hasUpdatedAt ? readUpdatedAt(row.getCell(COL_UPDATED_AT), line, errors) : null;

            if (errors.size() == before && id != null && name != null && quantity != null) {
                items.add(new Item(id, name, quantity, updatedAt, r));
            }
        }
        if (dataRows == 0) {
            errors.add(0, "데이터 행이 없습니다. 2행부터 품목을 입력해 주세요.");
        }
        if (!errors.isEmpty()) {
            throw invalid(errors);
        }
        return items;
    }

    private boolean headerMatches(Row header) {
        return header != null
                && HEADER_ID.equals(headerText(header.getCell(COL_ID)))
                && HEADER_NAME.equals(headerText(header.getCell(COL_NAME)))
                && HEADER_QUANTITY.equals(headerText(header.getCell(COL_QUANTITY)));
    }

    private boolean isEmptyRow(Row row, boolean hasUpdatedAt) {
        if (row == null) {
            return true;
        }
        int lastCol = hasUpdatedAt ? COL_UPDATED_AT : COL_QUANTITY;
        for (int c = COL_ID; c <= lastCol; c++) {
            if (!isBlank(row.getCell(c))) {
                return false;
            }
        }
        return true;
    }

    private Long readId(Cell cell, int line, List<String> errors) {
        Long value = readInteger(cell);
        if (value == null || value < 1) {
            addError(errors, line + "행: 품목ID는 1 이상의 정수여야 합니다.");
            return null;
        }
        return value;
    }

    private String readName(Cell cell, int line, List<String> errors) {
        if (cell == null || cell.getCellType() != CellType.STRING || cell.getStringCellValue().isBlank()) {
            addError(errors, line + "행: 품목명은 비어 있을 수 없습니다.");
            return null;
        }
        return cell.getStringCellValue().trim();
    }

    private Long readQuantity(Cell cell, int line, List<String> errors) {
        if (cell != null && cell.getCellType() == CellType.FORMULA) {
            addError(errors, line + "행: 현재 수량에는 수식을 사용할 수 없습니다.");
            return null;
        }
        Long value = readInteger(cell);
        if (value == null || value < Quantity.MIN || value > Quantity.MAX) {
            addError(errors, line + "행: 현재 수량은 0 이상 999,999,999,999,999 이하의 정수여야 합니다.");
            return null;
        }
        return value;
    }

    private LocalDateTime readUpdatedAt(Cell cell, int line, List<String> errors) {
        if (isBlank(cell)) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue();
        }
        addError(errors, line + "행: 최종 수정일시는 날짜/시간 값이어야 합니다.");
        return null;
    }

    /** 숫자 셀의 값이 정수이면 돌려주고, 그렇지 않으면 null. */
    private Long readInteger(Cell cell) {
        if (cell == null || cell.getCellType() != CellType.NUMERIC || DateUtil.isCellDateFormatted(cell)) {
            return null;
        }
        double d = cell.getNumericCellValue();
        if (Double.isNaN(d) || Double.isInfinite(d) || d != Math.rint(d) || Math.abs(d) > Quantity.MAX * 10.0) {
            return null;
        }
        return (long) d;
    }

    private void addError(List<String> errors, String message) {
        if (errors.size() < MAX_ERRORS) {
            errors.add(message);
        }
    }

    private InvalidRequestException invalid(List<String> errors) {
        List<String> shown = errors.size() > MAX_ERRORS ? errors.subList(0, MAX_ERRORS) : errors;
        return new InvalidRequestException(NOT_VALID + "\n" + String.join("\n", shown));
    }
}
