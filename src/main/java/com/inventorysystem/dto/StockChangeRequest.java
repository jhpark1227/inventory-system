package com.inventorysystem.dto;

import com.inventorysystem.domain.Quantity;
import com.inventorysystem.exception.InvalidRequestException;
import java.math.BigInteger;

/**
 * 입고/출고 요청. 소수나 문자열을 숫자로 바꿔 받아들이지 않도록 {@code quantity}를 원본 타입 그대로 받는다.
 */
public record StockChangeRequest(Object quantity) {

    private static final String INVALID = "수량은 0 이상 999,999,999,999,999 이하의 정수여야 합니다.";

    /** 정수만 허용하고 범위를 확인한 수량을 돌려준다. */
    public long toQuantity() {
        long value;
        if (quantity instanceof Integer || quantity instanceof Long) {
            value = ((Number) quantity).longValue();
        } else if (quantity instanceof BigInteger big && big.bitLength() < 64) {
            value = big.longValue();
        } else {
            throw new InvalidRequestException(INVALID);
        }
        if (value < Quantity.MIN || value > Quantity.MAX) {
            throw new InvalidRequestException(INVALID);
        }
        return value;
    }
}
