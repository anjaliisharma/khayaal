package com.khayaal.stock;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnitQuantityTest {
    @Test
    void packUnitsAcceptWholeValuesRegardlessOfFormatting() {
        assertThat(UnitQuantity.requirePositiveWholeUnits(new BigDecimal("30"), "unitsPerPack"))
                .isEqualByComparingTo("30");
        assertThat(UnitQuantity.requirePositiveWholeUnits(new BigDecimal("30.00"), "unitsPerPack"))
                .isEqualByComparingTo("30");
    }

    @Test
    void packUnitsRejectFractionsAndNonPositiveValues() {
        assertThatThrownBy(() -> UnitQuantity.requirePositiveWholeUnits(new BigDecimal("27.25"), "unitsPerPack"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("whole number");
        assertThatThrownBy(() -> UnitQuantity.requirePositiveWholeUnits(BigDecimal.ZERO, "unitsPerPack"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("positive");
    }
}
