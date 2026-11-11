package com.inventorysystem.dto;

import java.util.List;

public record ImportResponse(
        int importedCount,
        String fileName,
        boolean dirty,
        int changedCount,
        boolean unexported,
        List<ItemResponse> items) {
}
