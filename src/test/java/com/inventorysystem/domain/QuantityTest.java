package com.inventorysystem.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** UT-12: 수량 규칙 상수 */
class QuantityTest {

    /** 자바스크립트가 정확히 표현하는 가장 큰 정수 (2^53 - 1). */
    private static final long JS_MAX_SAFE_INTEGER = 9_007_199_254_740_991L;

    @Test
    @DisplayName("UT-12 최소값 0, 최대값은 15자리이며 JS 안전 정수 이하이다")
    void constants() {
        assertThat(Quantity.MIN).isZero();
        assertThat(Quantity.MAX).isEqualTo(999_999_999_999_999L);
        assertThat(String.valueOf(Quantity.MAX)).hasSize(15);
        assertThat(Quantity.MAX).isLessThanOrEqualTo(JS_MAX_SAFE_INTEGER);
    }
}
