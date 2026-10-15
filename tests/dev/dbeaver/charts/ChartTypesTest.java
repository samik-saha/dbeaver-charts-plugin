package dev.dbeaver.charts;

import java.util.Arrays;
import java.util.List;

public final class ChartTypesTest {
    private static int checks;

    public static void main(String[] args) {
        check(ChartGeometry.range(new double[]{5, 10}, ChartOptions.Type.LINE).min() == 5, "line keeps data range");
        check(ChartGeometry.range(new double[]{5, 10}, ChartOptions.Type.COLUMN).min() == 0, "columns include zero");
        check(ChartGeometry.range(new double[]{-5, -2}, ChartOptions.Type.BAR).max() == 0, "negative bars include zero");
        check(ChartGeometry.range(new double[]{0, 0}, ChartOptions.Type.BAR).max() == 1, "zero-only range is drawable");
        check(ChartGeometry.range(new double[]{Double.NaN}, ChartOptions.Type.BAR) == null, "null-only bars show empty state");
        var range = new ChartValues.Range(-10, 30);
        var vertical = new ChartGeometry.Box(0, 0, 100, 200);
        var positiveColumn = ChartGeometry.bar(vertical, range, 30, 0, 2, 70, false);
        var negativeColumn = ChartGeometry.bar(vertical, range, -10, 1, 2, 70, false);
        check(positiveColumn.y() == 0 && positiveColumn.height() == 150, "positive column ends at zero baseline");
        check(negativeColumn.y() == 150 && negativeColumn.height() == 50, "negative column extends below zero");
        check(positiveColumn.width() == 35 && negativeColumn.width() == 35, "consistent column width");
        var horizontal = new ChartGeometry.Box(0, 0, 200, 100);
        var positiveBar = ChartGeometry.bar(horizontal, range, 30, 0, 2, 70, true);
        var negativeBar = ChartGeometry.bar(horizontal, range, -10, 1, 2, 70, true);
        check(positiveBar.x() == 50 && positiveBar.width() == 150, "positive bar extends right of zero");
        check(negativeBar.x() == 0 && negativeBar.width() == 50, "negative bar extends left of zero");
        check(positiveBar.height() == 35, "orientation swaps width dimension");
        var zero = ChartGeometry.bar(horizontal, range, 0, 0, 1, 70, true);
        check(zero.x() == 50 && zero.width() == 0, "zero bar stays at baseline");
        check(ChartGeometry.center(0, 1, false) == 0.5 && ChartGeometry.center(0, 1, true) == 0.5,
            "single point and bar are centered");
        check(ChartGeometry.hit(0.249, 4, false) == 0 && ChartGeometry.hit(0.251, 4, false) == 1,
            "bar hover uses slots");
        check(ChartGeometry.hit(1, 4, false) == 3 && ChartGeometry.hit(-0.1, 4, false) == -1,
            "hover boundary and outside plot");
        check(ChartGeometry.hit(0.5, 3, true) == 1 && ChartGeometry.hit(0.5, 0, true) == -1,
            "line hover and empty plot");

        var series = new ChartSeries.Builder(ChartSeries.Aggregation.SUM);
        series.add("A", 2);
        series.add("B", 3);
        series.add("Z", 10);
        series.add("Z", 20);
        series.add("missing", null);
        check(series.build(1, ChartOptions.Order.VALUE_DESC).values()[0] == 30,
            "ranking happens after aggregation and before truncation");
        check(series.build(1, ChartOptions.Order.VALUE_ASC).details()[0].equals("A"), "value ascending");
        check(series.build(10, ChartOptions.Order.VALUE_DESC).details()[3].equals("missing"), "null values sort last");
        var numbers = new ChartSeries.Builder(ChartSeries.Aggregation.SUM);
        numbers.add(10, 1);
        numbers.add(2, 1);
        numbers.add(null, 1);
        check(numbers.build(10, ChartOptions.Order.CATEGORY_ASC).details()[0].equals("2"), "numeric categories sort numerically");
        check(numbers.build(10, ChartOptions.Order.CATEGORY_DESC).details()[2].equals("(null)"), "null category stays last descending");
        var dates = new ChartSeries.Builder(ChartSeries.Aggregation.SUM);
        dates.add("2026-09-16", 1);
        dates.add("2026-09-15", 2);
        check(dates.build(1, ChartOptions.Order.CATEGORY_DESC).details()[0].equals("2026-09-16"), "reverse date order");
        var groups = new ChartGroups.Builder(List.of("country"), ChartSeries.Aggregation.SUM, 50);
        groups.add(List.of("US"), "A", 10);
        groups.add(List.of("US"), "B", 20);
        groups.add(List.of("CA"), "A", 30);
        groups.add(List.of("CA"), "B", 2);
        var ranked = groups.build(1, ChartOptions.Order.VALUE_DESC);
        check(ranked.groups().get(0).series().details()[0].equals("B")
            && ranked.groups().get(1).series().details()[0].equals("A"), "group value ranking is independent");
        var categories = groups.build(2, ChartOptions.Order.CATEGORY_DESC);
        check(Arrays.equals(categories.groups().get(0).series().details(), categories.groups().get(1).series().details()),
            "category sorting retains shared grouped domain");
        var raw = new ChartSeries.Builder(ChartSeries.Aggregation.NONE);
        raw.add("A", 1);
        raw.add("A", 9);
        check(Arrays.equals(raw.build(2, ChartOptions.Order.VALUE_DESC).values(), new double[]{9, 1}),
            "None sorts rows without aggregating");
        check(ChartOptions.defaults().withType(ChartOptions.Type.BAR).barLimit() == 30, "type switch preserves options");
        System.out.println("ChartTypes: " + checks + " checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        checks++;
    }
}
