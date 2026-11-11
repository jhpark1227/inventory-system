package com.inventorysystem.dto;

import com.inventorysystem.domain.Item;
import java.time.LocalDateTime;

public record ItemResponse(
        long id,
        String name,
        long quantity,
        long savedQuantity,
        boolean changed,
        LocalDateTime updatedAt) {

    public static ItemResponse from(Item item) {
        return new ItemResponse(item.getId(), item.getName(), item.getQuantity(),
                item.getSavedQuantity(), item.isChanged(), item.getUpdatedAt());
    }
}
