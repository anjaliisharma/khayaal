package com.khayaal.stock;

import java.math.BigDecimal;

public final class UnitQuantity {
    private static final BigDecimal QUARTER_UNIT = new BigDecimal("0.25");

    private UnitQuantity() { }

    public static BigDecimal requireNonNegativeQuarterUnits(BigDecimal value, String fieldName) {
        if (value == null) throw new NullPointerException(fieldName + " is required");
        if (value.signum() < 0 || value.remainder(QUARTER_UNIT).signum() != 0) {
            throw new IllegalArgumentException(fieldName + " must be non-negative and use quarter-unit increments");
        }
        return value.stripTrailingZeros();
    }

    public static BigDecimal requirePositiveQuarterUnits(BigDecimal value, String fieldName) {
        BigDecimal validated = requireNonNegativeQuarterUnits(value, fieldName);
        if (validated.signum() == 0) throw new IllegalArgumentException(fieldName + " must be positive");
        return validated;
    }

    public static BigDecimal requirePositiveWholeUnits(BigDecimal value, String fieldName) {
        if (value == null) throw new NullPointerException(fieldName + " is required");
        if (value.signum() <= 0 || value.stripTrailingZeros().scale() > 0)
            throw new IllegalArgumentException(fieldName + " must be a positive whole number");
        return value.stripTrailingZeros();
    }
}

