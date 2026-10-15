package dev.dbeaver.charts;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;

public final class ChartSeriesTest {
    public static void main(String[] args) {
        var sum = new ChartSeries.Builder(ChartSeries.Aggregation.SUM);
        sum.add("A", new BigDecimal("0.1"));
        sum.add("B", null);
        sum.add("A", new BigDecimal("0.2"));
        sum.add("A", Double.NaN);
        var totals = sum.build(10);
        check(totals.values().length == 2 && totals.values()[0] == 0.3, "decimal SUM by X");
        check(Double.isNaN(totals.values()[1]), "all-null group remains a gap");
        check(totals.ignoredValues() == 2, "null and invalid accounting");
        for (var op : new ChartSeries.Aggregation[]{ChartSeries.Aggregation.AVG,
                ChartSeries.Aggregation.MIN, ChartSeries.Aggregation.MAX}) {
            var builder = new ChartSeries.Builder(op);
            builder.add("A", 2);
            builder.add("A", null);
            builder.add("A", 8);
            double expected = op == ChartSeries.Aggregation.AVG ? 5 : op == ChartSeries.Aggregation.MIN ? 2 : 8;
            check(builder.build(1).values()[0] == expected, op + " skips nulls");
        }
        var dates = new ChartSeries.Builder(ChartSeries.Aggregation.SUM);
        dates.add(java.sql.Date.valueOf("2026-09-16"), 2);
        dates.add(java.sql.Date.valueOf("2025-09-15"), 3);
        dates.add(java.sql.Date.valueOf("2026-09-16"), 4);
        var dateSeries = dates.build(10);
        check(dateSeries.chronological() && dateSeries.values()[0] == 3 && dateSeries.values()[1] == 6,
            "SQL dates sort and aggregate");
        check(dateSeries.labels()[0].endsWith("\n2025"), "date labels retain year");
        var timestamps = new ChartSeries.Builder(ChartSeries.Aggregation.SUM);
        timestamps.add(Timestamp.valueOf("2026-09-16 12:01:00.001"), 1);
        timestamps.add(Timestamp.valueOf("2026-09-16 12:01:00.002"), 2);
        var timed = timestamps.build(10);
        check(timed.totalPoints() == 2, "formatted labels never collapse distinct timestamps");
        check(timed.labels()[0].contains("\n12:01:00") && timed.details()[0].endsWith(".001"),
            "time on second line with full precision in hover");
        var raw = new ChartSeries.Builder(ChartSeries.Aggregation.NONE);
        raw.add(LocalDate.of(2026, 9, 16), 2);
        raw.add(LocalDate.of(2026, 9, 15), 1);
        raw.add(LocalDate.of(2026, 9, 16), 3);
        check(raw.build(10).values().length == 3 && raw.build(10).values()[0] == 2,
            "None preserves duplicate X values and row order");
        var limited = new ChartSeries.Builder(ChartSeries.Aggregation.SUM);
        limited.add("A", 1);
        limited.add("B", 9);
        limited.add("A", 10);
        check(limited.build(1).values()[0] == 11 && limited.build(1).totalPoints() == 2,
            "limit applies after complete aggregation");
        var numericKeys = new ChartSeries.Builder(ChartSeries.Aggregation.SUM);
        numericKeys.add(new BigDecimal("1.0"), 2);
        numericKeys.add(new BigDecimal("1.00"), 3);
        check(numericKeys.build(10).values()[0] == 5, "numeric key scale normalization");
        var isoDates = new ChartSeries.Builder(ChartSeries.Aggregation.SUM);
        isoDates.add("2026-09-16", 3);
        isoDates.add("2026-09-15", 2);
        check(isoDates.build(10).values()[0] == 2 && isoDates.build(10).chronological(),
            "ISO date strings");
        var instant = new ChartSeries.Builder(ChartSeries.Aggregation.SUM);
        instant.add(Instant.parse("2026-09-16T12:34:56Z"), 1);
        check(instant.build(1).details()[0].contains("UTC"), "instant timezone is explicit");
        var nullKey = new ChartSeries.Builder(ChartSeries.Aggregation.SUM);
        nullKey.add(null, 3);
        nullKey.add(null, 4);
        nullKey.add("(null)", 5);
        check(nullKey.build(10).totalPoints() == 2 && nullKey.build(10).values()[0] == 7,
            "real null is separate from text");
        var nullDate = new ChartSeries.Builder(ChartSeries.Aggregation.SUM);
        nullDate.add(null, 9);
        nullDate.add(LocalDate.of(2026, 9, 16), 1);
        check(nullDate.build(10).chronological() && nullDate.build(10).values()[1] == 9,
            "null X follows sorted dates");
        System.out.println("ChartSeries: 17 checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
