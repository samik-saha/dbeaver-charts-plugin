package dev.dbeaver.charts;

import java.util.Arrays;
import java.util.List;

public final class ChartCombinedTest {
    private static int checks;

    public static void main(String[] args) {
        check(ChartOptions.defaults().layout() == ChartOptions.Layout.COMBINED, "combined is default");
        check(ChartOptions.defaults().barMode() == ChartOptions.BarMode.GROUPED, "side by side is default");
        var builder = new ChartGroups.Builder(List.of("country"), ChartSeries.Aggregation.SUM, 50);
        builder.add(List.of("US"), "2026-09-16", 30);
        builder.add(List.of("CA"), "2026-09-15", 80);
        builder.add(List.of("US"), "2026-09-15", 10);
        builder.add(List.of("US"), "2026-09-15", 5);
        var combined = builder.build(30, ChartOptions.Order.AUTO, true).groups();
        check(Arrays.equals(combined.get(0).series().values(), new double[]{15, 30}), "aggregation preserved");
        check(Arrays.equals(combined.get(0).series().details(), combined.get(1).series().details()), "shared date axis");
        check(Double.isNaN(combined.get(1).series().values()[1]), "absent series point stays missing");
        var ranked = builder.build(1, ChartOptions.Order.VALUE_DESC, true).groups();
        check(ranked.get(0).series().details()[0].equals("2026-09-15")
            && ranked.get(1).series().details()[0].equals("2026-09-15"), "common ranking by combined total before limit");
        var separate = builder.build(1, ChartOptions.Order.VALUE_DESC, false).groups();
        check(separate.get(0).series().details()[0].equals("2026-09-16"), "separate ranking remains independent");
        var line = new ChartScene(combined, ChartOptions.defaults());
        check(line.range.min() == 15 && line.range.max() == 80, "combined line uses global value range");
        var column = new ChartScene(combined, options(ChartOptions.Type.COLUMN, ChartOptions.BarMode.GROUPED));
        check(column.range.min() == 0 && column.range.max() == 80, "grouped columns use global zero baseline");
        var stacked = new ChartScene(combined, options(ChartOptions.Type.COLUMN, ChartOptions.BarMode.STACKED));
        check(stacked.range.max() == 95 && stacked.starts[1][0] == 15 && stacked.ends[1][0] == 95,
            "stacked range uses cumulative totals");
        var signed = new ChartGroups.Builder(List.of("series"), ChartSeries.Aggregation.SUM, 50);
        signed.add(List.of("a"), "X", 20);
        signed.add(List.of("b"), "X", -10);
        signed.add(List.of("c"), "X", 30);
        signed.add(List.of("d"), "X", -15);
        var mixed = new ChartScene(signed.build(10, ChartOptions.Order.AUTO, true).groups(),
            options(ChartOptions.Type.BAR, ChartOptions.BarMode.STACKED));
        check(mixed.range.min() == -25 && mixed.range.max() == 50, "signed stacks accumulate on each side of zero");
        check(mixed.starts[2][0] == 20 && mixed.starts[3][0] == -10, "signed segments start at correct prior total");
        var plot = new ChartGeometry.Box(0, 0, 200, 100);
        var first = ChartGeometry.seriesBar(plot, new ChartValues.Range(0, 100), 0, 40, 0, 1, 0, 2, 70, false, false);
        var second = ChartGeometry.seriesBar(plot, new ChartValues.Range(0, 100), 0, 80, 0, 1, 1, 2, 70, false, false);
        check(first.x() + first.width() < second.x(), "side-by-side columns do not overlap");
        var hFirst = ChartGeometry.seriesBar(plot, new ChartValues.Range(0, 100), 0, 40, 0, 1, 0, 2, 70, true, false);
        var hSecond = ChartGeometry.seriesBar(plot, new ChartValues.Range(0, 100), 0, 80, 0, 1, 1, 2, 70, true, false);
        check(hFirst.y() + hFirst.height() < hSecond.y(), "side-by-side horizontal bars do not overlap");
        var base = ChartGeometry.seriesBar(plot, new ChartValues.Range(0, 100), 0, 40, 0, 1, 0, 2, 70, true, true);
        var top = ChartGeometry.seriesBar(plot, new ChartValues.Range(0, 100), 40, 100, 0, 1, 1, 2, 70, true, true);
        check(base.y() == top.y() && base.x() + base.width() == top.x(), "stack segments meet without overlap");
        var raw = new ChartGroups.Builder(List.of("series"), ChartSeries.Aggregation.NONE, 50);
        raw.add(List.of("a"), "X", 1);
        raw.add(List.of("a"), "X", 2);
        raw.add(List.of("b"), "X", 3);
        var rawGroups = raw.build(10, ChartOptions.Order.AUTO, true).groups();
        check(Arrays.equals(rawGroups.get(0).series().values(), new double[]{1, 2})
            && Double.isNaN(rawGroups.get(1).series().values()[1]), "None aligns duplicate X by occurrence without aggregation");
        var nulls = new ChartGroups.Builder(List.of("series"), ChartSeries.Aggregation.SUM, 50);
        nulls.add(List.of("a"), null, 1);
        nulls.add(List.of("b"), "(null)", 2);
        check(nulls.build(10, ChartOptions.Order.AUTO, true).groups().get(0).series().values().length == 2,
            "combined domain keeps null separate from null text");
        var huge = new ChartGroups.Builder(List.of("series"), ChartSeries.Aggregation.SUM, 50);
        huge.add(List.of("a"), "X", Double.MAX_VALUE);
        huge.add(List.of("b"), "X", Double.MAX_VALUE);
        var overflow = new ChartScene(huge.build(10, ChartOptions.Order.AUTO, true).groups(),
            options(ChartOptions.Type.COLUMN, ChartOptions.BarMode.STACKED));
        check(overflow.overflow && overflow.range == null, "overflowing stacks report unavailable range");
        System.out.println("ChartCombined: " + checks + " checks passed");
    }

    static ChartOptions options(ChartOptions.Type type, ChartOptions.BarMode mode) {
        return new ChartOptions(type, ChartOptions.Order.AUTO, false, true, 70, 30,
            ChartOptions.Layout.COMBINED, mode, true);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
