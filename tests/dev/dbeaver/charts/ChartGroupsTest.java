package dev.dbeaver.charts;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

public final class ChartGroupsTest {
    private static int checks;

    public static void main(String[] args) {
        var one = builder(List.of("country"), ChartSeries.Aggregation.SUM, 50);
        one.add(List.of("US"), "2026-09-16", 2);
        one.add(List.of("CA"), "2026-09-15", 8);
        one.add(List.of("US"), "2026-09-16", 3);
        one.add(List.of("US"), "2026-09-15", 4);
        var result = one.build(10);
        check(result.totalGroups() == 2 && result.rows() == 4, "one group per dimension value");
        check(result.groups().get(0).rows() == 3, "group row count");
        check(result.groups().get(0).title().equals("country = \"US\""), "labeled dimension");
        check(Arrays.equals(result.groups().get(0).series().values(), new double[]{4, 5}),
            "SUM within dimension and X");
        check(Arrays.equals(result.groups().get(0).series().details(), result.groups().get(1).series().details()),
            "common chronological X domain");
        check(result.groups().get(1).series().values()[0] == 8
            && Double.isNaN(result.groups().get(1).series().values()[1]), "missing X is a gap, not zero");

        var two = builder(List.of("country", "device"), ChartSeries.Aggregation.SUM, 50);
        two.add(List.of("US", "TV"), 1, 10);
        two.add(List.of("US", "Phone"), 1, 20);
        two.add(List.of("CA", "TV"), 1, 30);
        two.add(List.of("US", "TV"), 1, 5);
        var multi = two.build(10);
        check(multi.totalGroups() == 3 && multi.groups().get(0).series().values()[0] == 15,
            "multiple columns form a tuple");
        check(multi.groups().get(0).title().contains("device = \"TV\""), "all dimensions appear in label");
        var collision = builder(List.of("a", "b"), ChartSeries.Aggregation.SUM, 50);
        collision.add(List.of("a|b", "c"), 1, 10);
        collision.add(List.of("a", "b|c"), 1, 20);
        check(collision.build(10).totalGroups() == 2, "delimiter-like text does not collide");

        var nulls = builder(List.of("country"), ChartSeries.Aggregation.SUM, 50);
        nulls.add(Arrays.asList((Object) null), 1, 4);
        nulls.add(Arrays.asList((Object) null), 1, null);
        nulls.add(List.of("(null)"), 1, 8);
        check(nulls.build(10).totalGroups() == 2 && nulls.build(10).groups().get(0).series().values()[0] == 4,
            "null dimension is grouped separately from literal null text");
        var decimals = builder(List.of("bucket"), ChartSeries.Aggregation.SUM, 50);
        decimals.add(List.of(new BigDecimal("1.0")), 1, 3);
        decimals.add(List.of(new BigDecimal("1.00")), 1, 5);
        check(decimals.build(10).totalGroups() == 1 && decimals.build(10).groups().get(0).series().values()[0] == 8,
            "dimension numeric scale is normalized");

        for (var aggregation : List.of(ChartSeries.Aggregation.AVG, ChartSeries.Aggregation.MIN, ChartSeries.Aggregation.MAX)) {
            var values = builder(List.of("country"), aggregation, 50);
            values.add(List.of("US"), 1, 2);
            values.add(List.of("CA"), 1, 100);
            values.add(List.of("US"), 1, 8);
            double expected = aggregation == ChartSeries.Aggregation.AVG ? 5
                : aggregation == ChartSeries.Aggregation.MIN ? 2 : 8;
            check(values.build(10).groups().get(0).series().values()[0] == expected, aggregation + " remains per group");
        }

        var raw = builder(List.of("country"), ChartSeries.Aggregation.NONE, 50);
        raw.add(List.of("US"), "2026-09-16", 1);
        raw.add(List.of("CA"), "2026-09-15", 9);
        raw.add(List.of("US"), "2026-09-16", 2);
        check(Arrays.equals(raw.build(10).groups().get(0).series().values(), new double[]{1, 2}),
            "None preserves raw rows within each group");
        var limited = builder(List.of("country"), ChartSeries.Aggregation.SUM, 1);
        limited.add(List.of("US"), 1, 2);
        limited.add(List.of("CA"), 1, 100);
        limited.add(List.of("US"), 1, 8);
        limited.add(List.of("US"), 2, 7);
        var capped = limited.build(1);
        check(capped.totalGroups() == 2 && capped.groups().size() == 1, "group limit reports omitted groups");
        check(capped.groups().get(0).series().values()[0] == 10
            && capped.groups().get(0).series().totalPoints() == 2, "group and point limits preserve complete totals");

        var noDimensions = builder(List.of(), ChartSeries.Aggregation.SUM, 50);
        noDimensions.add(List.of(), "day", 2);
        noDimensions.add(List.of(), "day", 3);
        check(noDimensions.build(10).totalGroups() == 1
            && noDimensions.build(10).groups().get(0).series().values()[0] == 5, "clearing dimensions restores one chart");
        check(builder(List.of("country"), ChartSeries.Aggregation.SUM, 50).build(10).groups().isEmpty(), "empty results");
        var invalidY = builder(List.of("country"), ChartSeries.Aggregation.SUM, 50);
        invalidY.add(List.of("US"), 1, null);
        invalidY.add(List.of("CA"), 1, 3);
        check(Double.isNaN(invalidY.build(10).groups().get(0).series().values()[0]), "all-null group remains visible as a gap");
        // Adding rows after an earlier build must invalidate the cached X domain.
        invalidY.add(List.of("US"), 2, 6);
        check(invalidY.build(10).groups().get(0).series().values().length == 2, "domain updates after additional rows");
        System.out.println("ChartGroups: " + checks + " checks passed");
    }

    private static ChartGroups.Builder builder(List<String> dimensions, ChartSeries.Aggregation aggregation, int limit) {
        return new ChartGroups.Builder(dimensions, aggregation, limit);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        checks++;
    }
}
