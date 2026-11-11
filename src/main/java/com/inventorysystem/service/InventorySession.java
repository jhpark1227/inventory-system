package com.inventorysystem.service;

import com.inventorysystem.domain.Item;
import com.inventorysystem.domain.Quantity;
import com.inventorysystem.exception.InvalidRequestException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

/**
 * 사용자 세션의 서버 메모리 데이터. 원본 파일 바이트, 파일명, 품목별 저장본/작업본 수량을 보관한다.
 * 입고/출고는 작업본만 바꾸고, {@link #save}로 저장본에 확정한다.
 */
@Component
@SessionScope
public class InventorySession {

    private String fileName;
    private byte[] originalBytes;
    private List<Item> items = new ArrayList<>();
    private boolean unexported;
    private boolean loaded;

    /** 파일 내용으로 세션 데이터를 통째로 교체한다. 저장본과 작업본은 같고 변경 상태는 초기화된다. */
    public void load(String fileName, byte[] originalBytes, List<Item> items) {
        this.fileName = fileName;
        this.originalBytes = originalBytes;
        this.items = new ArrayList<>(items);
        this.unexported = false;
        this.loaded = true;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public String getFileName() {
        return fileName;
    }

    public byte[] getOriginalBytes() {
        return originalBytes;
    }

    public List<Item> getItems() {
        return Collections.unmodifiableList(items);
    }

    public boolean isUnexported() {
        return unexported;
    }

    public int changedCount() {
        return (int) items.stream().filter(Item::isChanged).count();
    }

    public boolean isDirty() {
        return changedCount() > 0;
    }

    public Item inbound(long id, long quantity) {
        validate(quantity);
        Item item = find(id);
        if (quantity > Quantity.MAX - item.getQuantity()) {
            throw new InvalidRequestException(
                    "입고 후 수량이 상한(" + format(Quantity.MAX) + "개)을 넘을 수 없습니다. 현재 재고는 "
                            + format(item.getQuantity()) + "개입니다.");
        }
        item.setQuantity(item.getQuantity() + quantity);
        return item;
    }

    public Item outbound(long id, long quantity) {
        validate(quantity);
        Item item = find(id);
        if (quantity > item.getQuantity()) {
            throw new InvalidRequestException("재고가 부족합니다. 현재 재고는 " + format(item.getQuantity()) + "개입니다.");
        }
        item.setQuantity(item.getQuantity() - quantity);
        return item;
    }

    /** 변경된 품목만 저장본으로 확정하고 최종 수정일시를 갱신한다. 저장한 품목 수를 돌려준다. */
    public int save(LocalDateTime now) {
        LocalDateTime savedAt = now.truncatedTo(ChronoUnit.SECONDS);
        int saved = 0;
        for (Item item : items) {
            if (item.isChanged()) {
                item.commit(savedAt);
                saved++;
            }
        }
        if (saved > 0) {
            unexported = true;
        }
        return saved;
    }

    /** 저장하지 않은 변경을 버리고 마지막 저장 상태로 복원한다. */
    public void discard() {
        items.forEach(Item::revert);
    }

    public void markExported() {
        this.unexported = false;
    }

    private Item find(long id) {
        return items.stream()
                .filter(item -> item.getId() == id)
                .findFirst()
                .orElseThrow(() -> new InvalidRequestException("존재하지 않는 품목입니다. (품목ID: " + id + ")"));
    }

    private void validate(long quantity) {
        if (quantity < Quantity.MIN || quantity > Quantity.MAX) {
            throw new InvalidRequestException(
                    "수량은 0 이상 " + format(Quantity.MAX) + " 이하의 정수여야 합니다.");
        }
    }

    private static String format(long value) {
        return String.format("%,d", value);
    }
}
