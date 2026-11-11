package com.inventorysystem.dto;

import java.util.List;

public record SaveResponse(
        int savedCount,
        String fileName,
        boolean dirty,
        int changedCount,
        boolean unexported,
        List<ItemResponse> items) {
}
