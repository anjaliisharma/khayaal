package com.khayaal.stock;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StockLedgerPropertyTest {
    @Property
    void ledgerBalanceEqualsTheSumAndUndoRestoresIt(@ForAll List<@net.jqwik.api.constraints.IntRange(min = 1, max = 100) Integer> values) {
        List<StockLedgerEntry> entries = new ArrayList<>();
        values.forEach(value -> entries.add(new StockLedgerEntry(BigDecimal.valueOf(value))));
        BigDecimal beforeDose = new StockLedger().balance(entries);
        entries.add(new StockLedgerEntry(new BigDecimal("-0.25")));
        entries.add(new StockLedgerEntry(new BigDecimal("0.25")));
        assertThat(new StockLedger().balance(entries)).isEqualByComparingTo(beforeDose);
    }
}

