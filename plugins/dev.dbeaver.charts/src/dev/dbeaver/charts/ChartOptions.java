package dev.dbeaver.charts;

public record ChartOptions(Type type, Order order, boolean showValues, boolean showGrid,
                           int barWidth, int barLimit, Layout layout, BarMode barMode, boolean showLegend) {
    public enum Layout {
        COMBINED("Combined chart"), SEPARATE("Separate charts");
        private final String label;
        Layout(String label) { this.label = label; }
        public String label() { return label; }
    }

    public enum BarMode {
        GROUPED("Side by side"), STACKED("Stacked");
        private final String label;
        BarMode(String label) { this.label = label; }
        public String label() { return label; }
    }

    public enum Type {
        LINE("Line"), COLUMN("Column (vertical)"), BAR("Bar (horizontal)");
        private final String label;
        Type(String label) { this.label = label; }
        public String label() { return label; }
    }

    public enum Order {
        AUTO("Automatic / result order"),
        CATEGORY_ASC("Category ascending"), CATEGORY_DESC("Category descending"),
        VALUE_ASC("Value ascending"), VALUE_DESC("Value descending");
        private final String label;
        Order(String label) { this.label = label; }
        public String label() { return label; }
        public boolean byValue() { return this == VALUE_ASC || this == VALUE_DESC; }
    }

    public ChartOptions {
        if (type == null || order == null || layout == null || barMode == null
            || barWidth < 20 || barWidth > 100 || barLimit < 1 || barLimit > 200) {
            throw new IllegalArgumentException("Invalid chart options");
        }
    }

    public static ChartOptions defaults() {
        return new ChartOptions(Type.LINE, Order.AUTO, false, true, 70, 30,
            Layout.COMBINED, BarMode.GROUPED, true);
    }

    public ChartOptions withType(Type type) {
        return new ChartOptions(type, order, showValues, showGrid, barWidth, barLimit, layout, barMode, showLegend);
    }

    public ChartOptions withLayout(Layout layout) {
        return new ChartOptions(type, order, showValues, showGrid, barWidth, barLimit, layout, barMode, showLegend);
    }
}
