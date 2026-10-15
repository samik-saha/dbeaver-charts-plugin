package dev.dbeaver.charts;

import java.math.BigDecimal;

/** Runs without SWT, a database, or a test dependency. */
public final class ChartValuesTest {
    public static void main(String[] args) {
        check(ChartValues.numeric(new BigDecimal("12.5")) == 12.5, "decimal input");
        check(Double.isNaN(ChartValues.numeric(null)), "null is a gap");
        check(Double.isNaN(ChartValues.numeric("12")), "strings are not numeric data");
        check(Double.isNaN(ChartValues.numeric(Double.POSITIVE_INFINITY)), "infinity is a gap");
        check(ChartValues.range(new double[]{Double.NaN}) == null, "empty series");
        check(ChartValues.range(new double[]{7, 7}).fraction(7) == 0.5, "constant series");
        var negative = ChartValues.range(new double[]{-10, Double.NaN, -5});
        check(negative.fraction(-10) == 0 && negative.fraction(-5) == 1, "negative range");
        var huge = ChartValues.range(new double[]{-Double.MAX_VALUE, Double.MAX_VALUE});
        check(huge.fraction(0) == 0.5, "overflow-safe scale");
        var tiny = ChartValues.range(new double[]{0, Double.MIN_VALUE});
        check(tiny.fraction(Double.MIN_VALUE) == 1, "subnormal scale");
        check(new ChartValues.Range(0, 999).axisLabels().max().equals("999"), "no unit below one thousand");
        check(new ChartValues.Range(0, 1_000).axisLabels().max().equals("1K"), "thousand boundary");
        check(new ChartValues.Range(1_200, 12_500).axisLabels().min().equals("1.2K"), "decimal thousands");
        check(new ChartValues.Range(0, 1_000_000).axisLabels().max().equals("1M"), "million boundary");
        check(new ChartValues.Range(250_000, 1_250_000).axisLabels().min().equals("0.25M"), "shared axis unit");
        check(new ChartValues.Range(0, 2_500_000_000d).axisLabels().max().equals("2.5B"), "billions");
        check(new ChartValues.Range(-2_500_000, -1_200_000).axisLabels().min().equals("-2.5M"), "negative scale");
        check(new ChartValues.Range(-2_000, 1_000).axisLabels().max().equals("1K"), "mixed signs");
        check(new ChartValues.Range(0, 0).axisLabels().min().equals("0"), "zero constant");
        var close = new ChartValues.Range(1_000_001, 1_000_002).axisLabels();
        check(close.min().equals("1.000001M") && close.max().equals("1.000002M"), "close bounds retain precision");
        check(new BigDecimal(new ChartValues.Range(0.00001, 0.00002).axisLabels().min())
            .compareTo(new BigDecimal("0.00001")) == 0, "small nonzero labels");
        System.out.println("ChartValues: 20 checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
