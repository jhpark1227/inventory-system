package com.inventorysystem.dto;

public record ChangeResponse(
        ItemResponse item,
        boolean dirty,
        int changedCount,
        boolean unexported) {
}
