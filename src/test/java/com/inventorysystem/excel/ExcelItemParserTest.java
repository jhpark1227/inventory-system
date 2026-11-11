package com.inventorysystem.excel;

import static com.inventorysystem.support.ExcelFixtures.HEADER;
import static com.inventorysystem.support.ExcelFixtures.sample;
import static com.inventorysystem.support.ExcelFixtures.stock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.inventorysystem.domain.Item;
import com.inventorysystem.exception.InvalidRequestException;
import com.inventorysystem.support.ExcelFixtures.Formula;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** UT-06, UT-07: 엑셀 파싱과 검증 */
class ExcelItemParserTest {

    private final ExcelItemParser parser = new ExcelItemParser();

    @Test
    @DisplayName("UT-06 정상 파일은 행 순서대로 품목으로 변환된다 (D열 없음)")
    void parsesValidFile() {
        List<Item> items = parser.parse(sample());

        assertThat(items).extracting(Item::getId).containsExactly(1L, 2L, 3L);
        assertThat(items).extracting(Item::getName).containsExactly("무선마우스", "키보드", "USB 메모리");
        assertThat(items).extracting(Item::getQuantity).containsExactly(30L, 20L, 50L);
        assertThat(items).extracting(Item::getUpdatedAt).containsOnlyNulls();
        assertThat(items).extracting(Item::getRowIndex).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("UT-07 잘못된 파일은 오류 행 번호와 이유를 메시지에 담아 거부한다")
    void rejectsInvalidRows() {
        byte[] file = stock(HEADER,
                new Object[] {1, "정상", 10},
                new Object[] {2, "소수", 2.5},
                new Object[] {3, "음수", -1},
                new Object[] {4, "문자", "열"},
                new Object[] {5, "수식", new Formula("1+1")},
                new Object[] {1, "중복", 7},
                new Object[] {7, " ", 1});

        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("3행: 현재 수량")
                .hasMessageContaining("4행: 현재 수량")
                .hasMessageContaining("5행: 현재 수량")
                .hasMessageContaining("6행: 현재 수량에는 수식")
                .hasMessageContaining("7행: 품목ID 1가 중복")
                .hasMessageContaining("8행: 품목명");
    }

    @Test
    @DisplayName("UT-07 헤더가 다르거나 xlsx가 아니면 거부한다")
    void rejectsWrongHeaderAndWrongFormat() {
        byte[] wrongHeader = stock(new Object[] {"ID", "이름", "수량"}, new Object[] {1, "마우스", 1});

        assertThatThrownBy(() -> parser.parse(wrongHeader))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("1행");
        assertThatThrownBy(() -> parser.parse("엑셀이 아닙니다".getBytes()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining(".xlsx");
    }
}
