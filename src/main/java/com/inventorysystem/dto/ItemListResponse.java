package com.inventorysystem.dto;

import java.util.List;

public record ItemListResponse(
        String fileName,
        boolean dirty,
        int changedCount,
        boolean unexported,
        List<ItemResponse> items) {
}
