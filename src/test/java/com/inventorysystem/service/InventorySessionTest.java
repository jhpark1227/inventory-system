package com.inventorysystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.inventorysystem.domain.Item;
import com.inventorysystem.domain.Quantity;
import com.inventorysystem.exception.InvalidRequestException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** UT-01 ~ UT-05: 수량 규칙, 저장, 되돌리기 */
class InventorySessionTest {

    private static final long MOUSE = 1;
    private static final long KEYBOARD = 2;
    private static final long USB = 3;

    private InventorySession session;

    @BeforeEach
    void setUp() {
        session = new InventorySession();
        session.load("inventory.xlsx", new byte[0], List.of(
                new Item(MOUSE, "무선마우스", 30, null, 1),
                new Item(KEYBOARD, "키보드", 20, null, 2),
                new Item(USB, "USB 메모리", 50, null, 3)));
    }

    private Item item(long id) {
        return session.getItems().stream().filter(i -> i.getId() == id).findFirst().orElseThrow();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("UT-01 입고/출고는 작업본만 바꾼다")
    void inboundAndOutboundChangeOnlyDraft() {
        session.inbound(KEYBOARD, 5);
        session.outbound(KEYBOARD, 3);

        Item keyboard = item(KEYBOARD);
        assertThat(keyboard.getQuantity()).isEqualTo(22);
        assertThat(keyboard.getSavedQuantity()).isEqualTo(20);
        assertThat(keyboard.isChanged()).isTrue();
        assertThat(session.isDirty()).isTrue();
        assertThat(session.changedCount()).isEqualTo(1);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("UT-02 규칙에 어긋난 요청은 거부하고 수량을 바꾸지 않는다")
    void rejectInvalidRequests() {
        assertThatThrownBy(() -> session.outbound(KEYBOARD, 21))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("재고가 부족합니다")
                .hasMessageContaining("20");
        assertThatThrownBy(() -> session.inbound(KEYBOARD, Quantity.MAX))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("상한");
        assertThatThrownBy(() -> session.inbound(KEYBOARD, -1))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> session.inbound(KEYBOARD, Quantity.MAX + 1))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> session.inbound(99, 1))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("존재하지 않는 품목");

        assertThat(item(KEYBOARD).getQuantity()).isEqualTo(20);
        assertThat(session.isDirty()).isFalse();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("UT-03 입고 후 같은 수량을 출고하면 변경으로 보지 않는다")
    void revertingToSavedQuantityIsNotAChange() {
        session.inbound(KEYBOARD, 5);
        session.outbound(KEYBOARD, 5);

        assertThat(item(KEYBOARD).isChanged()).isFalse();
        assertThat(session.isDirty()).isFalse();
        assertThat(session.save(LocalDateTime.now())).isZero();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("UT-04 저장은 변경된 품목만 저장본으로 확정한다")
    void saveCommitsOnlyChangedItems() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 11, 15, 20, 41, 123_000_000);
        session.inbound(KEYBOARD, 5);

        int saved = session.save(now);

        assertThat(saved).isEqualTo(1);
        assertThat(item(KEYBOARD).getSavedQuantity()).isEqualTo(25);
        assertThat(item(KEYBOARD).getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 11, 15, 20, 41));
        assertThat(item(MOUSE).getUpdatedAt()).isNull();
        assertThat(session.isDirty()).isFalse();
        assertThat(session.isUnexported()).isTrue();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("UT-05 되돌리기는 마지막 저장 상태로 복원한다")
    void discardRestoresLastSavedState() {
        session.inbound(KEYBOARD, 5);
        session.save(LocalDateTime.now());
        session.inbound(KEYBOARD, 10);
        session.outbound(USB, 1);

        session.discard();

        assertThat(item(KEYBOARD).getQuantity()).isEqualTo(25);
        assertThat(item(KEYBOARD).getSavedQuantity()).isEqualTo(25);
        assertThat(item(USB).getQuantity()).isEqualTo(50);
        assertThat(session.isDirty()).isFalse();
        assertThat(session.isUnexported()).isTrue();
    }
}
