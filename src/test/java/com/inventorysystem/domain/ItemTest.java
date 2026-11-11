package com.inventorysystem.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** UT-10, UT-11: 품목의 저장본/작업본 상태 */
class ItemTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 11, 11, 30, 0);

    private Item keyboard() {
        return new Item(2, "키보드", 20, CREATED, 2);
    }

    @Test
    @DisplayName("UT-10 만든 직후에는 저장본과 작업본이 같고 변경으로 보지 않는다")
    void newItemIsUnchanged() {
        Item item = keyboard();

        assertThat(item.getId()).isEqualTo(2);
        assertThat(item.getName()).isEqualTo("키보드");
        assertThat(item.getSavedQuantity()).isEqualTo(20);
        assertThat(item.getQuantity()).isEqualTo(20);
        assertThat(item.isChanged()).isFalse();
        assertThat(item.getUpdatedAt()).isEqualTo(CREATED);
        assertThat(item.getRowIndex()).isEqualTo(2);
    }

    @Test
    @DisplayName("UT-11 수량 변경은 작업본만 바꾸고, 저장된 값으로 돌아오면 변경이 아니다")
    void changeAffectsOnlyDraft() {
        Item item = keyboard();

        item.setQuantity(25);
        assertThat(item.isChanged()).isTrue();
        assertThat(item.getSavedQuantity()).isEqualTo(20);

        item.setQuantity(20);
        assertThat(item.isChanged()).isFalse();
    }

    @Test
    @DisplayName("UT-11 commit은 작업본을 저장본으로 확정하고 수정일시를 갱신한다")
    void commitConfirmsDraft() {
        Item item = keyboard();
        LocalDateTime savedAt = LocalDateTime.of(2026, 11, 11, 15, 20, 41);
        item.setQuantity(25);

        item.commit(savedAt);

        assertThat(item.getSavedQuantity()).isEqualTo(25);
        assertThat(item.getQuantity()).isEqualTo(25);
        assertThat(item.isChanged()).isFalse();
        assertThat(item.getUpdatedAt()).isEqualTo(savedAt);
    }

    @Test
    @DisplayName("UT-11 revert는 작업본을 저장본으로 복원하고 수정일시는 건드리지 않는다")
    void revertRestoresSavedQuantity() {
        Item item = keyboard();
        item.setQuantity(99);

        item.revert();

        assertThat(item.getQuantity()).isEqualTo(20);
        assertThat(item.getSavedQuantity()).isEqualTo(20);
        assertThat(item.isChanged()).isFalse();
        assertThat(item.getUpdatedAt()).isEqualTo(CREATED);
    }
}
