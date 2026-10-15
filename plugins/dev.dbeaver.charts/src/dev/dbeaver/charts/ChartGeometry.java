package dev.dbeaver.charts;

/** Shared rendering geometry; keeps zero baselines and hit testing consistent across orientations. */
public final class ChartGeometry {
    private ChartGeometry() {}
    public record Box(int x, int y, int width, int height) {}

    public static ChartValues.Range range(double[] values, ChartOptions.Type type) {
        ChartValues.Range range = ChartValues.range(values);
        if (range == null || type == ChartOptions.Type.LINE) {
            return range;
        }
        double min = Math.min(0, range.min());
        double max = Math.max(0, range.max());
        return min == max ? new ChartValues.Range(0, 1) : new ChartValues.Range(min, max);
    }

    public static double center(int index, int count, boolean line) {
        return line ? count <= 1 ? 0.5 : index / (double) (count - 1) : (index + 0.5) / count;
    }

    public static int hit(double fraction, int count, boolean line) {
        if (count < 1 || fraction < 0 || fraction > 1 || !Double.isFinite(fraction)) {
            return -1;
        }
        return Math.min(count - 1, line ? (int) Math.round(fraction * (count - 1)) : (int) (fraction * count));
    }

    public static Box bar(Box plot, ChartValues.Range range, double value, int index,
                          int count, int widthPercent, boolean horizontal) {
        double start = range.fraction(0);
        double end = range.fraction(value);
        int cross = horizontal ? plot.height : plot.width;
        double slot = cross / (double) count;
        int thickness = Math.max(1, (int) Math.floor(slot * widthPercent / 100));
        int offset = (int) Math.round(center(index, count, false) * cross - thickness / 2.0);
        if (horizontal) {
            int zero = plot.x + (int) Math.round(start * plot.width);
            int tip = plot.x + (int) Math.round(end * plot.width);
            return new Box(Math.min(zero, tip), plot.y + offset, Math.abs(tip - zero), thickness);
        }
        int zero = plot.y + (int) Math.round((1 - start) * plot.height);
        int tip = plot.y + (int) Math.round((1 - end) * plot.height);
        return new Box(plot.x + offset, Math.min(zero, tip), thickness, Math.abs(tip - zero));
    }

    public static Box seriesBar(Box plot, ChartValues.Range range, double start, double end, int index,
                                int count, int series, int seriesCount, int widthPercent,
                                boolean horizontal, boolean stacked) {
        int cross = horizontal ? plot.height : plot.width;
        double slot = cross / (double) count;
        double total = slot * widthPercent / 100;
        double width = stacked ? total : total / seriesCount;
        double center = (index + 0.5) * slot - total / 2
            + (stacked ? total / 2 : (series + 0.5) * width);
        int thickness = Math.max(1, (int) Math.floor(width * (!stacked && seriesCount > 1 ? 0.9 : 1)));
        int offset = (int) Math.round(center - thickness / 2.0);
        int base = horizontal ? plot.x + (int) Math.round(range.fraction(start) * plot.width)
            : plot.y + (int) Math.round((1 - range.fraction(start)) * plot.height);
        int tip = horizontal ? plot.x + (int) Math.round(range.fraction(end) * plot.width)
            : plot.y + (int) Math.round((1 - range.fraction(end)) * plot.height);
        return horizontal
            ? new Box(Math.min(base, tip), plot.y + offset, Math.abs(tip - base), thickness)
            : new Box(plot.x + offset, Math.min(base, tip), thickness, Math.abs(tip - base));
    }
}
