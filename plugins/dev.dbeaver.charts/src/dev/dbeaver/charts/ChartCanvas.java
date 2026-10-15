package dev.dbeaver.charts;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import java.util.ArrayList;
import java.util.List;

/** Native SWT chart surface shared by line, column, and horizontal bar renderers. */
final class ChartCanvas extends Canvas {
    private double[] values = new double[0];
    private String[] labels = new String[0];
    private String[] details = new String[0];
    private String seriesName = "";
    private ChartScene scene = new ChartScene(List.of(), ChartOptions.defaults());
    private final List<Color> colors = new ArrayList<>();
    private final List<Rectangle> valueLabelBounds = new ArrayList<>();
    private ChartOptions options = ChartOptions.defaults();
    private ChartGeometry.Box plot;
    private int axisLabelWidth;
    private String emptyMessage = "Run a query and select a numeric value column.";

    ChartCanvas(Composite parent) {
        super(parent, SWT.DOUBLE_BUFFERED);
        addPaintListener(event -> paintChart(event.gc));
        addDisposeListener(event -> colors.forEach(Color::dispose));
        addMouseMoveListener(event -> {
            if (plot == null || event.x < plot.x() || event.x > plot.x() + plot.width()
                || event.y < plot.y() || event.y > plot.y() + plot.height()) {
                setToolTipText(null);
                return;
            }
            double fraction = horizontal() ? (event.y - plot.y()) / (double) plot.height()
                : (event.x - plot.x()) / (double) plot.width();
            int index = ChartGeometry.hit(fraction, values.length, line());
            if (index < 0) {
                setToolTipText(null);
                return;
            }
            int selected = -1;
            int nearest = 13;
            for (int s = 0; s < scene.groups.size(); s++) {
                double value = scene.groups.get(s).series().values()[index];
                if (!Double.isFinite(value)) {
                    continue;
                }
                if (line()) {
                    int distance = Math.abs(valuePosition(scene.range, value) - event.y);
                    if (distance < nearest) {
                        nearest = distance;
                        selected = s;
                    }
                } else {
                    ChartGeometry.Box bar = bar(s, index);
                    if (event.x >= bar.x() - 1 && event.x <= bar.x() + bar.width() + 1
                        && event.y >= bar.y() - 1 && event.y <= bar.y() + bar.height() + 1) {
                        selected = s;
                        break;
                    }
                }
            }
            StringBuilder tip = new StringBuilder(details[index]).append("\n").append(seriesName);
            for (int s = 0; s < scene.groups.size(); s++) {
                if (selected >= 0 && selected != s) {
                    continue;
                }
                if (selected < 0 && s == 10) {
                    tip.append("\n… ").append(scene.groups.size() - 10).append(" more series");
                    break;
                }
                ChartGroups.Group group = scene.groups.get(s);
                double value = group.series().values()[index];
                tip.append("\n").append(group.title()).append(": ")
                    .append(Double.isFinite(value) ? Double.toString(value) : "No numeric value");
            }
            setToolTipText(tip.toString());
        });
    }

    void setSeries(ChartSeries.Series series, String emptyMessage, String seriesName, ChartOptions options) {
        setGroups(List.of(new ChartGroups.Group("All rows", series, 0)), emptyMessage, seriesName, options, 0);
    }

    void setGroups(List<ChartGroups.Group> groups, String emptyMessage, String seriesName, ChartOptions options, int colorOffset) {
        scene = new ChartScene(groups, options);
        values = groups.isEmpty() ? new double[0] : groups.get(0).series().values();
        labels = groups.isEmpty() ? new String[0] : groups.get(0).series().labels();
        details = groups.isEmpty() ? new String[0] : groups.get(0).series().details();
        colors.forEach(Color::dispose);
        colors.clear();
        for (int i = 0; i < groups.size(); i++) {
            colors.add(ChartPalette.color(getDisplay(), colorOffset + i));
        }
        this.options = options;
        this.seriesName = seriesName;
        this.emptyMessage = emptyMessage;
        plot = null;
        setToolTipText(null);
        redraw();
    }

    int minimumWidth() {
        int slot = options.barMode() == ChartOptions.BarMode.GROUPED ? Math.max(32, scene.groups.size() * 14) : 32;
        return options.type() == ChartOptions.Type.COLUMN ? Math.max(260, Math.min(20_000, values.length * slot + 110))
            : horizontal() ? 320 : 180;
    }

    int preferredHeight(boolean grouped) {
        int slot = options.barMode() == ChartOptions.BarMode.GROUPED ? Math.max(28, scene.groups.size() * 14) : 28;
        return horizontal() ? Math.max(200, Math.min(20_000, values.length * slot + 65)) : grouped ? 180 : 300;
    }

    int requiredAxisWidth() {
        ChartValues.Range range = scene.range;
        if (range == null) {
            return 0;
        }
        GC gc = new GC(this);
        try {
            int widest = 0;
            if (horizontal()) {
                for (String label : labels) {
                    widest = Math.max(widest, gc.textExtent(oneLine(label)).x);
                }
                return Math.min(180, widest);
            }
            for (double tick : ticks(range)) {
                widest = Math.max(widest, gc.textExtent(range.axisLabel(tick)).x);
            }
            return widest;
        } finally {
            gc.dispose();
        }
    }

    void setAxisLabelWidth(int width) {
        axisLabelWidth = width;
        redraw();
    }

    private boolean horizontal() { return options.type() == ChartOptions.Type.BAR; }
    private boolean line() { return options.type() == ChartOptions.Type.LINE; }

    private void paintChart(GC gc) {
        Rectangle area = getClientArea();
        plot = null;
        gc.setBackground(getBackground());
        gc.fillRectangle(area);
        gc.setForeground(getForeground());
        ChartValues.Range range = scene.range;
        if (range == null) {
            gc.drawText(scene.overflow ? "Stacked totals exceed the numeric plotting range." : emptyMessage, 12, 12, true);
            return;
        }
        if (area.width < 180 || area.height < 130) {
            gc.drawText("Enlarge the chart panel.", 8, 8, true);
            return;
        }
        int left = Math.max(axisLabelWidth, requiredAxisWidth()) + (horizontal() ? 24 : 16);
        if (horizontal()) {
            left = Math.min(left, Math.max(60, area.width / 3));
        }
        int top = 24;
        int bottom = horizontal() ? 32 : gc.getFontMetrics().getHeight() * 2 + 24;
        int right = horizontal() ? 36 : 24;
        if (area.width - left - right < 20) {
            return;
        }
        plot = new ChartGeometry.Box(left, top, area.width - left - right, area.height - top - bottom);
        gc.setAntialias(SWT.ON);
        drawAxes(gc, range);
        drawCategoryLabels(gc);
        for (int s = 0; s < scene.groups.size(); s++) {
            gc.setForeground(colors.get(s));
            gc.setBackground(colors.get(s));
            if (line()) {
                drawLine(gc, range, s);
            } else {
                drawBars(gc, range, s);
            }
        }
        valueLabelBounds.clear();
        if (options.showValues()) {
            for (int s = 0; s < scene.groups.size(); s++) {
                drawValues(gc, range, s);
            }
        }
    }

    private void drawAxes(GC gc, ChartValues.Range range) {
        gc.setLineWidth(1);
        int fontHeight = gc.getFontMetrics().getHeight();
        int lastLabelEnd = Integer.MIN_VALUE;
        for (double tick : ticks(range)) {
            int coordinate = valuePosition(range, tick);
            if (options.showGrid()) {
                gc.setAlpha(45);
                if (horizontal()) {
                    gc.drawLine(coordinate, plot.y(), coordinate, plot.y() + plot.height());
                } else {
                    gc.drawLine(plot.x(), coordinate, plot.x() + plot.width(), coordinate);
                }
                gc.setAlpha(255);
            }
            String label = range.axisLabel(tick);
            if (horizontal()) {
                int textWidth = gc.textExtent(label).x;
                int x = clamp(coordinate - textWidth / 2, plot.x(), plot.x() + plot.width() - textWidth);
                if (x > lastLabelEnd + 6) {
                    gc.drawText(label, x, plot.y() + plot.height() + 8, true);
                    lastLabelEnd = x + textWidth;
                }
            } else {
                gc.drawText(label, plot.x() - gc.textExtent(label).x - 10, coordinate - fontHeight / 2, true);
            }
        }
        gc.drawLine(plot.x(), plot.y() + plot.height(), plot.x() + plot.width(), plot.y() + plot.height());
        if (horizontal()) {
            int zero = valuePosition(range, 0);
            gc.drawLine(zero, plot.y(), zero, plot.y() + plot.height());
        } else {
            gc.drawLine(plot.x(), plot.y(), plot.x(), plot.y() + plot.height());
            if (!line() || range.min() < 0 && range.max() > 0) {
                int zero = valuePosition(range, 0);
                gc.drawLine(plot.x(), zero, plot.x() + plot.width(), zero);
            }
        }
    }

    private void drawCategoryLabels(GC gc) {
        if (horizontal()) {
            for (int i = 0; i < labels.length; i++) {
                String label = fit(gc, oneLine(labels[i]), plot.x() - 18);
                gc.drawText(label, plot.x() - gc.textExtent(label).x - 10,
                    categoryPosition(i) - gc.getFontMetrics().getHeight() / 2, true);
            }
            return;
        }
        int widest = 1;
        for (String label : labels) {
            widest = Math.max(widest, gc.textExtent(label).x);
        }
        int tickCount = Math.min(labels.length, Math.max(1, plot.width() / (widest + 20)));
        if (line()) {
            tickCount = Math.min(6, tickCount);
        }
        int labelWidth = Math.max(10, plot.width() / Math.max(1, tickCount) - 12);
        for (int tick = 0; tick < tickCount; tick++) {
            int index = tickCount == 1 ? 0 : tick * (labels.length - 1) / (tickCount - 1);
            String text = java.util.Arrays.stream(labels[index].split("\n", -1))
                .map(part -> fit(gc, part, labelWidth)).collect(java.util.stream.Collectors.joining("\n"));
            int x = clamp(categoryPosition(index) - gc.textExtent(text).x / 2,
                plot.x(), plot.x() + plot.width() - gc.textExtent(text).x);
            gc.drawText(text, x, plot.y() + plot.height() + 8, true);
        }
    }

    private void drawLine(GC gc, ChartValues.Range range, int series) {
        double[] values = scene.groups.get(series).series().values();
        gc.setLineWidth(2);
        boolean connected = false;
        int previousX = 0;
        int previousY = 0;
        for (int i = 0; i < values.length; i++) {
            if (!Double.isFinite(values[i])) {
                connected = false;
                continue;
            }
            int x = categoryPosition(i);
            int y = valuePosition(range, values[i]);
            if (connected) {
                gc.drawLine(previousX, previousY, x, y);
            }
            gc.fillOval(x - 2, y - 2, 5, 5);
            previousX = x;
            previousY = y;
            connected = true;
        }
    }

    private void drawBars(GC gc, ChartValues.Range range, int series) {
        double[] values = scene.groups.get(series).series().values();
        gc.setLineWidth(1);
        for (int i = 0; i < values.length; i++) {
            if (!Double.isFinite(values[i])) {
                continue;
            }
            ChartGeometry.Box bar = bar(series, i);
            if (values[i] == 0) {
                gc.drawLine(bar.x(), bar.y(), bar.x() + bar.width(), bar.y() + bar.height());
            } else {
                gc.fillRectangle(bar.x(), bar.y(), bar.width(), bar.height());
            }
        }
    }

    private ChartGeometry.Box bar(int series, int index) {
        return ChartGeometry.seriesBar(plot, scene.range, scene.starts[series][index], scene.ends[series][index],
            index, values.length, series, scene.groups.size(), options.barWidth(), horizontal(),
            options.barMode() == ChartOptions.BarMode.STACKED);
    }

    private void drawValues(GC gc, ChartValues.Range range, int series) {
        double[] values = scene.groups.get(series).series().values();
        gc.setForeground(getForeground());
        gc.setBackground(getBackground());
        int lastRight = Integer.MIN_VALUE;
        int fontHeight = gc.getFontMetrics().getHeight();
        for (int i = 0; i < values.length; i++) {
            if (!Double.isFinite(values[i])) {
                continue;
            }
            String text = range.axisLabel(values[i]);
            int width = gc.textExtent(text).x;
            int category = categoryPosition(i);
            int value = valuePosition(range, values[i]);
            ChartGeometry.Box box = line() ? null : bar(series, i);
            if (box != null) {
                category = horizontal() ? box.y() + box.height() / 2 : box.x() + box.width() / 2;
            }
            if (box != null && options.barMode() == ChartOptions.BarMode.STACKED && scene.groups.size() > 1) {
                if (width + 4 <= box.width() && fontHeight + 2 <= box.height()) {
                    drawValueLabel(gc, text, box.x() + (box.width() - width) / 2,
                        box.y() + (box.height() - fontHeight) / 2, box, series);
                }
                continue;
            }
            if (horizontal()) {
                int x = values[i] >= 0 ? value + 5 : value - width - 5;
                if (box.height() >= fontHeight) {
                    drawValueLabel(gc, text, clamp(x, plot.x(), plot.x() + plot.width() - width),
                        category - fontHeight / 2, box, series);
                }
            } else {
                int x = clamp(category - width / 2, plot.x(), plot.x() + plot.width() - width);
                if (x > lastRight + 4) {
                    int y = values[i] < 0 && !line() ? value + 3 : value - fontHeight - 3;
                    drawValueLabel(gc, text, x, clamp(y, 1, plot.y() + plot.height() - fontHeight), box, series);
                    lastRight = x + width;
                }
            }
        }
    }

    private void drawValueLabel(GC gc, String text, int x, int y, ChartGeometry.Box bar, int series) {
        Rectangle bounds = new Rectangle(x - 2, y - 1, gc.textExtent(text).x + 4, gc.getFontMetrics().getHeight() + 2);
        if (valueLabelBounds.stream().noneMatch(previous -> previous.intersects(bounds))) {
            boolean inside = bar != null && x >= bar.x() && y >= bar.y()
                && x + gc.textExtent(text).x <= bar.x() + bar.width()
                && y + gc.getFontMetrics().getHeight() <= bar.y() + bar.height();
            Color color = colors.get(series);
            double luminance = 0.2126 * linear(color.getRed()) + 0.7152 * linear(color.getGreen())
                + 0.0722 * linear(color.getBlue());
            gc.setForeground(inside ? getDisplay().getSystemColor(luminance < 0.179 ? SWT.COLOR_WHITE : SWT.COLOR_BLACK)
                : getForeground());
            gc.drawText(text, x, y, true);
            valueLabelBounds.add(bounds);
        }
    }

    private static double linear(int component) {
        double channel = component / 255.0;
        return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private int categoryPosition(int index) {
        double fraction = ChartGeometry.center(index, values.length, line());
        return horizontal() ? plot.y() + (int) Math.round(fraction * plot.height())
            : plot.x() + (int) Math.round(fraction * plot.width());
    }

    private int valuePosition(ChartValues.Range range, double value) {
        double fraction = range.fraction(value);
        return horizontal() ? plot.x() + (int) Math.round(fraction * plot.width())
            : plot.y() + (int) Math.round((1 - fraction) * plot.height());
    }

    private static double[] ticks(ChartValues.Range range) {
        if (range.min() == range.max()) {
            return new double[]{range.min()};
        }
        double[] ticks = new double[5];
        for (int i = 0; i < ticks.length; i++) {
            double fraction = i / 4.0;
            ticks[i] = range.min() * (1 - fraction) + range.max() * fraction;
        }
        return ticks;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private static String oneLine(String text) {
        return text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
    }

    private static String fit(GC gc, String text, int width) {
        String result = oneLine(text);
        if (gc.textExtent(result).x <= width) {
            return result;
        }
        while (!result.isEmpty() && gc.textExtent(result + "…").x > width) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "…";
    }
}
