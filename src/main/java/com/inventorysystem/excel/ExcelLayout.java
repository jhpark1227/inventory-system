package com.inventorysystem.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

/** 엑셀 파일의 시트와 열 배치 규칙. 파서와 익스포터가 함께 쓴다. */
final class ExcelLayout {

    static final String SHEET_NAME = "재고";
    static final String HEADER_ID = "품목ID";
    static final String HEADER_NAME = "품목명";
    static final String HEADER_QUANTITY = "현재 수량";
    static final String HEADER_UPDATED_AT = "최종 수정일시";

    static final int COL_ID = 0;
    static final int COL_NAME = 1;
    static final int COL_QUANTITY = 2;
    static final int COL_UPDATED_AT = 3;

    static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private ExcelLayout() {
    }

    /** {@code 재고} 시트를 쓰고, 없으면 첫 번째 시트를 쓴다. */
    static Sheet findSheet(Workbook workbook) {
        Sheet sheet = workbook.getSheet(SHEET_NAME);
        if (sheet == null && workbook.getNumberOfSheets() > 0) {
            sheet = workbook.getSheetAt(0);
        }
        return sheet;
    }

    /** 셀이 없거나, 비어 있거나, 공백 문자열이면 비어 있는 것으로 본다. */
    static boolean isBlank(Cell cell) {
        if (cell == null) {
            return true;
        }
        return switch (cell.getCellType()) {
            case BLANK -> true;
            case STRING -> cell.getStringCellValue().isBlank();
            default -> false;
        };
    }

    static String headerText(Cell cell) {
        if (cell == null || cell.getCellType() != CellType.STRING) {
            return "";
        }
        return cell.getStringCellValue().trim();
    }
}
