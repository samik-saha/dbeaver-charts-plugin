package dev.dbeaver.charts;

import java.util.List;

/** Shared scale and signed stack endpoints for a rendered chart. */
final class ChartScene {
    final List<ChartGroups.Group> groups;
    final double[][] starts;
    final double[][] ends;
    final ChartValues.Range range;
    final boolean overflow;

    ChartScene(List<ChartGroups.Group> groups, ChartOptions options) {
        this.groups = List.copyOf(groups);
        int count = groups.isEmpty() ? 0 : groups.get(0).series().values().length;
        starts = new double[groups.size()][count];
        ends = new double[groups.size()][count];
        double[] positive = new double[count];
        double[] negative = new double[count];
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        boolean failed = false;
        boolean bars = options.type() != ChartOptions.Type.LINE;
        boolean stacked = bars && options.barMode() == ChartOptions.BarMode.STACKED;
        for (int s = 0; s < groups.size(); s++) {
            double[] values = groups.get(s).series().values();
            if (values.length != count) {
                throw new IllegalArgumentException("Combined series must have an aligned category domain");
            }
            for (int i = 0; i < count; i++) {
                double value = values[i];
                if (!Double.isFinite(value)) {
                    ends[s][i] = Double.NaN;
                    continue;
                }
                double start = stacked ? value >= 0 ? positive[i] : negative[i] : 0;
                double end = start + value;
                starts[s][i] = start;
                ends[s][i] = end;
                if (!Double.isFinite(end)) {
                    failed = true;
                    continue;
                }
                if (stacked) {
                    if (value >= 0) {
                        positive[i] = end;
                    } else {
                        negative[i] = end;
                    }
                }
                min = Math.min(min, bars ? Math.min(start, end) : value);
                max = Math.max(max, bars ? Math.max(start, end) : value);
            }
        }
        overflow = failed;
        range = failed || !Double.isFinite(min) ? null
            : bars && min == max ? new ChartValues.Range(0, 1) : new ChartValues.Range(min, max);
    }
}
