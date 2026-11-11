package com.inventorysystem.excel;

import static com.inventorysystem.excel.ExcelLayout.*;

import com.inventorysystem.domain.Item;
import com.inventorysystem.exception.ExportException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * 원본 엑셀 파일을 열어 각 품목의 저장본 수량(C열)과 최종 수정일시(D열)만 갱신한 새 파일을 만든다.
 * 서식, 다른 열, 다른 시트는 유지된다.
 */
@Component
public class ExcelExporter {

    public byte[] export(byte[] original, List<Item> items) {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(original));
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = findSheet(workbook);
            if (sheet == null) {
                throw new IllegalStateException("시트를 찾을 수 없습니다.");
            }
            CellStyle dateStyle = null;
            Row header = sheet.getRow(0);
            if (!HEADER_UPDATED_AT.equals(headerText(header.getCell(COL_UPDATED_AT)))) {
                Cell headerCell = header.createCell(COL_UPDATED_AT);
                headerCell.setCellValue(HEADER_UPDATED_AT);
                Cell quantityHeader = header.getCell(COL_QUANTITY);
                if (quantityHeader != null) {
                    headerCell.setCellStyle(quantityHeader.getCellStyle());
                }
                sheet.setColumnWidth(COL_UPDATED_AT, 20 * 256);
            }
            for (Item item : items) {
                Row row = sheet.getRow(item.getRowIndex());
                if (row == null) {
                    throw new IllegalStateException("행을 찾을 수 없습니다: " + (item.getRowIndex() + 1));
                }
                row.getCell(COL_QUANTITY, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                        .setCellValue((double) item.getSavedQuantity());

                Cell updatedAt = row.getCell(COL_UPDATED_AT, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                if (item.getUpdatedAt() == null) {
                    updatedAt.setBlank();
                } else {
                    if (!DateUtil.isCellDateFormatted(updatedAt)) {
                        if (dateStyle == null) {
                            dateStyle = createDateStyle(workbook);
                        }
                        updatedAt.setCellStyle(dateStyle);
                    }
                    updatedAt.setCellValue(item.getUpdatedAt());
                }
            }
            // 사용자가 만든 다른 수식이 수정된 수량을 참조할 수 있으므로, 엑셀이 열 때 다시 계산하게 한다.
            workbook.setForceFormulaRecalculation(true);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException | RuntimeException e) {
            throw new ExportException("엑셀 파일을 만들지 못했습니다.", e);
        }
    }

    private CellStyle createDateStyle(Workbook workbook) {
        CreationHelper helper = workbook.getCreationHelper();
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(helper.createDataFormat().getFormat(DATE_FORMAT));
        return style;
    }
}
