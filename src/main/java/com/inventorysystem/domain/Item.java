package com.inventorysystem.domain;

import java.time.LocalDateTime;

/**
 * 품목. 저장본 수량({@code savedQuantity})과 작업본 수량({@code quantity})을 함께 가진다.
 */
public class Item {

    private final long id;
    private final String name;
    private final int rowIndex;
    private long savedQuantity;
    private long quantity;
    private LocalDateTime updatedAt;

    public Item(long id, String name, long quantity, LocalDateTime updatedAt, int rowIndex) {
        this.id = id;
        this.name = name;
        this.rowIndex = rowIndex;
        this.savedQuantity = quantity;
        this.quantity = quantity;
        this.updatedAt = updatedAt;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /** 원본 엑셀 파일에서의 행 위치(0부터 시작). */
    public int getRowIndex() {
        return rowIndex;
    }

    public long getSavedQuantity() {
        return savedQuantity;
    }

    public long getQuantity() {
        return quantity;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isChanged() {
        return quantity != savedQuantity;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }

    /** 작업본 수량을 저장본으로 확정한다. */
    public void commit(LocalDateTime now) {
        this.savedQuantity = quantity;
        this.updatedAt = now;
    }

    /** 작업본 수량을 저장본 수량으로 되돌린다. */
    public void revert() {
        this.quantity = savedQuantity;
    }
}
