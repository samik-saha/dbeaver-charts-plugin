package dev.dbeaver.charts;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** UI-independent conversion and scaling for a single numeric series. */
public final class ChartValues {
    private ChartValues() {}

    public static double numeric(Object value) {
        if (value instanceof Number number) {
            double result = number.doubleValue();
            return Double.isFinite(result) ? result : Double.NaN;
        }
        return Double.NaN;
    }

    public record Range(double min, double max) {
        public AxisLabels axisLabels() {
            return new AxisLabels(axisLabel(min), axisLabel(max));
        }

        public String axisLabel(double value) {
            double magnitude = Math.max(Math.abs(min), Math.abs(max));
            int exponent = magnitude >= 1_000_000_000 ? 9
                : magnitude >= 1_000_000 ? 6 : magnitude >= 1_000 ? 3 : 0;
            String suffix = switch (exponent) {
                case 9 -> "B";
                case 6 -> "M";
                case 3 -> "K";
                default -> "";
            };
            BigDecimal lower = BigDecimal.valueOf(min).scaleByPowerOfTen(-exponent);
            BigDecimal upper = BigDecimal.valueOf(max).scaleByPowerOfTen(-exponent);
            int precision = 4;
            // Keep close bounds distinguishable, e.g. 1.000001M and 1.000002M.
            while (precision < 17 && min != max
                && lower.round(context(precision)).compareTo(upper.round(context(precision))) == 0) {
                precision++;
            }
            return label(BigDecimal.valueOf(value).scaleByPowerOfTen(-exponent), precision) + suffix;
        }

        public double fraction(double value) {
            if (min == max) {
                return 0.5;
            }
            // Scaling first avoids overflow when max - min exceeds Double.MAX_VALUE.
            double scale = Math.max(Math.abs(min), Math.abs(max));
            return (value / scale - min / scale) / (max / scale - min / scale);
        }
    }

    public record AxisLabels(String min, String max) {}

    private static MathContext context(int precision) {
        return new MathContext(precision, RoundingMode.HALF_UP);
    }

    private static String label(BigDecimal value, int precision) {
        BigDecimal rounded = value.round(context(precision)).stripTrailingZeros();
        // Keep ordinary labels decimal; avoid hundreds of characters for extreme values.
        if (rounded.signum() != 0
            && (rounded.abs().compareTo(new BigDecimal("0.0001")) < 0
                || rounded.abs().compareTo(new BigDecimal("1000000")) >= 0)) {
            return rounded.toEngineeringString();
        }
        return rounded.toPlainString();
    }

    public static Range range(double[] values) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            if (Double.isFinite(value)) {
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }
        return Double.isFinite(min) ? new Range(min, max) : null;
    }
}
