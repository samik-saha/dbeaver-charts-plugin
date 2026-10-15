package dev.dbeaver.charts;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.IContributionManager;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.jkiss.dbeaver.model.DBPDataKind;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.data.DBDAttributeBinding;
import org.jkiss.dbeaver.ui.controls.resultset.IResultSetController;
import org.jkiss.dbeaver.ui.controls.resultset.IResultSetPanel;
import org.jkiss.dbeaver.ui.controls.resultset.IResultSetPresentation;
import org.jkiss.dbeaver.ui.controls.resultset.ResultSetModel;
import org.jkiss.dbeaver.ui.controls.resultset.ResultSetRow;

/** A read-only chart of the rows DBeaver has already fetched. */
public final class ChartsPanel implements IResultSetPanel {
    private static final int MAX_POINTS = 10_000;
    private static final int MAX_GROUPS = 50;
    private IResultSetController controller;
    private Composite root;
    private Combo xColumn;
    private Combo yColumn;
    private Combo aggregation;
    private Combo chartType;
    private ChartOptions options = ChartOptions.defaults();
    private Button groupBy;
    private Label status;
    private ScrolledComposite chartScroll;
    private Composite chartContent;
    private final List<ChartCanvas> charts = new ArrayList<>();
    private final List<CLabel> headers = new ArrayList<>();
    private final List<ChartLegend> legends = new ArrayList<>();
    private List<DBDAttributeBinding> dimensions = List.of();
    private List<DBDAttributeBinding> attributes = List.of();
    private List<DBDAttributeBinding> numericAttributes = List.of();

    @Override
    public Control createContents(IResultSetPresentation presentation, Composite parent) {
        controller = presentation.getController();
        root = new Composite(parent, SWT.NONE);
        GridLayout rootLayout = new GridLayout(1, false);
        rootLayout.marginWidth = 4;
        rootLayout.marginHeight = 4;
        rootLayout.verticalSpacing = 4;
        root.setLayout(rootLayout);
        // Compact toolbar: three label/control pairs per row instead of one pair
        // per row, so the six fields take two rows rather than six.
        Composite toolbar = new Composite(root, SWT.NONE);
        toolbar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        GridLayout toolbarLayout = new GridLayout(6, false);
        toolbarLayout.marginWidth = 0;
        toolbarLayout.marginHeight = 0;
        toolbarLayout.horizontalSpacing = 6;
        toolbarLayout.verticalSpacing = 4;
        toolbar.setLayout(toolbarLayout);
        new Label(toolbar, SWT.NONE).setText("Chart type");
        chartType = new Combo(toolbar, SWT.READ_ONLY);
        chartType.setItems(java.util.Arrays.stream(ChartOptions.Type.values())
            .map(ChartOptions.Type::label).toArray(String[]::new));
        chartType.select(options.type().ordinal());
        chartType.setLayoutData(controlData(90));
        chartType.addListener(SWT.Selection, event -> {
            options = options.withType(ChartOptions.Type.values()[chartType.getSelectionIndex()]);
            updateChart();
        });
        new Label(toolbar, SWT.NONE).setText("Category / X");
        xColumn = new Combo(toolbar, SWT.READ_ONLY);
        xColumn.setLayoutData(controlData(120));
        new Label(toolbar, SWT.NONE).setText("Value");
        yColumn = new Combo(toolbar, SWT.READ_ONLY);
        yColumn.setLayoutData(controlData(120));
        new Label(toolbar, SWT.NONE).setText("Series view");
        Combo seriesView = new Combo(toolbar, SWT.READ_ONLY);
        seriesView.setItems(java.util.Arrays.stream(ChartOptions.Layout.values())
            .map(ChartOptions.Layout::label).toArray(String[]::new));
        seriesView.select(options.layout().ordinal());
        seriesView.setLayoutData(controlData(90));
        seriesView.addListener(SWT.Selection, event -> {
            options = options.withLayout(ChartOptions.Layout.values()[seriesView.getSelectionIndex()]);
            updateChart();
        });
        new Label(toolbar, SWT.NONE).setText("Aggregate");
        aggregation = new Combo(toolbar, SWT.READ_ONLY);
        aggregation.setItems(java.util.Arrays.stream(ChartSeries.Aggregation.values())
            .map(ChartSeries.Aggregation::label).toArray(String[]::new));
        aggregation.select(0);
        aggregation.setToolTipText("Combine loaded rows with the same X value. None preserves individual rows.");
        aggregation.setLayoutData(controlData(90));
        aggregation.addListener(SWT.Selection, event -> updateChart());
        new Label(toolbar, SWT.NONE).setText("Group by");
        groupBy = new Button(toolbar, SWT.PUSH);
        groupBy.setText("None (single chart)…");
        groupBy.setLayoutData(controlData(120));
        groupBy.addListener(SWT.Selection, event -> {
            DimensionSelectionDialog dialog = new DimensionSelectionDialog(root.getShell(), attributes, dimensions);
            if (dialog.open() == Window.OK) {
                dimensions = dialog.selected();
                updateDimensionButton();
                updateChart();
            }
        });
        Button displayOptions = new Button(toolbar, SWT.PUSH);
        displayOptions.setText("Display options…");
        GridData displayData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
        displayData.horizontalSpan = 3;
        displayOptions.setLayoutData(displayData);
        displayOptions.addListener(SWT.Selection, event -> {
            ChartOptionsDialog dialog = new ChartOptionsDialog(root.getShell(), options);
            if (dialog.open() == Window.OK) {
                options = dialog.options();
                updateChart();
            }
        });
        Button refresh = new Button(toolbar, SWT.PUSH);
        refresh.setText("Refresh");
        GridData refreshData = new GridData(SWT.RIGHT, SWT.CENTER, true, false);
        refreshData.horizontalSpan = 3;
        refresh.setLayoutData(refreshData);
        refresh.setToolTipText("Redraw from loaded rows without running the query again");
        refresh.addListener(SWT.Selection, event -> refresh(true));
        xColumn.addListener(SWT.Selection, event -> updateChart());
        yColumn.addListener(SWT.Selection, event -> updateChart());
        chartScroll = new ScrolledComposite(root, SWT.V_SCROLL | SWT.H_SCROLL);
        chartScroll.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        chartScroll.setExpandHorizontal(true);
        chartScroll.setExpandVertical(true);
        chartScroll.setShowFocusedControl(true);
        chartContent = new Composite(chartScroll, SWT.NONE);
        chartContent.setLayout(new GridLayout(1, false));
        chartScroll.setContent(chartContent);
        chartScroll.addListener(SWT.Resize, event -> layoutCharts());
        status = new Label(root, SWT.WRAP);
        status.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        refresh(true);
        return root;
    }

    @Override
    public void refresh(boolean force) {
        if (root == null || root.isDisposed()) {
            return;
        }
        List<DBDAttributeBinding> current = controller.getModel().getVisibleAttributes();
        if (!attributes.equals(current)) {
            String oldX = xColumn.getText();
            String oldY = yColumn.getText();
            List<String> previousDimensions = dimensions.stream().map(ChartsPanel::label).toList();
            attributes = new ArrayList<>(current);
            dimensions = attributes.stream().filter(column -> previousDimensions.contains(label(column))).toList();
            updateDimensionButton();
            numericAttributes = attributes.stream()
                .filter(attribute -> attribute.getDataKind() == DBPDataKind.NUMERIC).toList();
            xColumn.removeAll();
            xColumn.add("Row number");
            for (DBDAttributeBinding attribute : attributes) {
                xColumn.add(label(attribute));
            }
            yColumn.removeAll();
            for (DBDAttributeBinding attribute : numericAttributes) {
                yColumn.add(label(attribute));
            }
            int defaultX = 0;
            // Prefer a date dimension, then a nonnumeric column, instead of unique row numbers.
            for (int i = 0; i < attributes.size(); i++) {
                if (attributes.get(i).getDataKind() == DBPDataKind.DATETIME) {
                    defaultX = i + 1;
                    break;
                }
                if (defaultX == 0 && attributes.get(i).getDataKind() != DBPDataKind.NUMERIC) {
                    defaultX = i + 1;
                }
            }
            int previousX = xColumn.indexOf(oldX);
            xColumn.select(previousX >= 0 ? previousX : defaultX);
            if (!numericAttributes.isEmpty()) {
                yColumn.select(Math.max(0, yColumn.indexOf(oldY)));
            }
        }
        // Also initialize the row-number option for an empty result set.
        if (xColumn.getItemCount() == 0) {
            xColumn.add("Row number");
            xColumn.select(0);
        }
        updateChart();
    }

    private void updateChart() {
        ResultSetModel model = controller.getModel();
        int yIndex = yColumn.getSelectionIndex();
        if (yIndex < 0 || yIndex >= numericAttributes.size()) {
            renderGroups(List.of(), "Select a result with a numeric column.", "");
            status.setText("No numeric Y column available.");
            root.layout();
            return;
        }
        DBDAttributeBinding y = numericAttributes.get(yIndex);
        int xIndex = xColumn.getSelectionIndex() - 1;
        DBDAttributeBinding x = xIndex >= 0 && xIndex < attributes.size() ? attributes.get(xIndex) : null;
        List<ResultSetRow> rows = model.getAllRows();
        ChartSeries.Aggregation operation = ChartSeries.Aggregation.values()[aggregation.getSelectionIndex()];
        ChartGroups.Builder builder = new ChartGroups.Builder(
            dimensions.stream().map(ChartsPanel::label).toList(), operation, MAX_GROUPS);
        // Aggregate ALL loaded rows before limiting rendered points, so sums are never partial.
        for (int index = 0; index < rows.size(); index++) {
            ResultSetRow row = rows.get(index);
            Object xValue = x == null ? index + 1 : model.getCellValue(x, row);
            Object yValue = model.getCellValue(y, row);
            List<Object> dimensionValues = new ArrayList<>();
            for (DBDAttributeBinding dimension : dimensions) {
                Object value = model.getCellValue(dimension, row);
                dimensionValues.add(DBUtils.isNullValue(value) ? null : value);
            }
            builder.add(dimensionValues, DBUtils.isNullValue(xValue) ? null : xValue,
                DBUtils.isNullValue(yValue) ? null : yValue);
        }
        int pointLimit = options.type() == ChartOptions.Type.LINE ? MAX_POINTS : options.barLimit();
        boolean combined = options.layout() == ChartOptions.Layout.COMBINED;
        ChartGroups.Result result = builder.build(pointLimit, options.order(), combined);
        int missing = 0;
        int points = 0;
        int ignored = 0;
        boolean truncated = false;
        boolean chronological = false;
        for (ChartGroups.Group group : result.groups()) {
            ChartSeries.Series series = group.series();
            for (double value : series.values()) {
                if (!Double.isFinite(value)) {
                    missing++;
                }
            }
            points += series.values().length;
            ignored += series.ignoredValues();
            truncated |= series.totalPoints() > pointLimit;
            chronological |= series.chronological();
        }
        String seriesName = operation == ChartSeries.Aggregation.NONE ? y.getName()
            : operation.label() + "(" + y.getName() + ")";
        renderGroups(result.groups(), rows.isEmpty() ? "No rows loaded." : "No finite numeric values to plot.", seriesName);
        status.setText(options.type().label() + " • " + seriesName + " • " + rows.size()
            + " loaded rows • " + points + " plotted positions"
            + (dimensions.isEmpty() ? "" : " • Showing " + result.groups().size() + " of " + result.totalGroups() + " groups")
            + (missing == 0 ? "" : " • " + missing + " gaps")
            + (ignored == 0 ? "" : " • " + ignored + " null/invalid values ignored in shown groups")
            + (truncated ? " • First " + pointLimit + " categories per chart shown after ordering" : "")
            + (options.order() != ChartOptions.Order.AUTO ? " • " + options.order().label()
                : chronological ? " • Dates sorted, equally spaced" : " • Categories in result order")
            + (dimensions.isEmpty() ? "" : combined ? " • Shared value scale and category positions"
                : " • Independent value scales"
                + (operation == ChartSeries.Aggregation.NONE || options.order().byValue()
                    ? " • Category order per group" : " • Shared category positions")));
        root.layout(true, true);
        layoutCharts();
    }

    private void updateDimensionButton() {
        groupBy.setText(dimensions.isEmpty() ? "None (single chart)…"
            : dimensions.size() + (dimensions.size() == 1 ? " dimension…" : " dimensions…"));
        groupBy.setToolTipText(dimensions.isEmpty() ? "Create colored series using one or more dimension columns"
            : dimensions.stream().map(ChartsPanel::label).collect(java.util.stream.Collectors.joining("\n")));
    }

    private void renderGroups(List<ChartGroups.Group> groups, String emptyMessage, String seriesName) {
        boolean combined = options.layout() == ChartOptions.Layout.COMBINED;
        int count = combined ? 1 : Math.max(1, groups.size());
        if (charts.size() != count) {
            for (Control child : chartContent.getChildren()) {
                child.dispose();
            }
            charts.clear();
            headers.clear();
            legends.clear();
            for (int i = 0; i < count; i++) {
                CLabel header = new CLabel(chartContent, SWT.NONE);
                header.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
                headers.add(header);
                charts.add(new ChartCanvas(chartContent));
                legends.add(new ChartLegend(chartContent));
            }
        }
        int axisWidth = 0;
        for (int i = 0; i < count; i++) {
            boolean grouped = !dimensions.isEmpty() && !groups.isEmpty();
            CLabel header = headers.get(i);
            header.setVisible(grouped);
            ((GridData) header.getLayoutData()).exclude = !grouped;
            ChartGroups.Group group = groups.isEmpty() ? null : groups.get(i);
            String title = combined ? groups.size() + " series · Shared value scale"
                : group == null ? "" : group.title() + "  (" + group.rows() + " rows)";
            header.setText(title.replace("&", "&&"));
            header.setToolTipText(title);
            ChartCanvas chart = charts.get(i);
            GridData layout = new GridData(SWT.FILL, SWT.FILL, true, count == 1);
            List<ChartGroups.Group> displayed = combined ? groups : group == null ? List.of() : List.of(group);
            chart.setGroups(displayed, emptyMessage, seriesName, options, combined ? 0 : i);
            legends.get(i).setGroups(displayed, combined ? 0 : i, options.showLegend());
            layout.heightHint = chart.preferredHeight(count > 1);
            chart.setLayoutData(layout);
            axisWidth = Math.max(axisWidth, chart.requiredAxisWidth());
        }
        for (ChartCanvas chart : charts) {
            chart.setAxisLabelWidth(axisWidth);
        }
        layoutCharts();
    }

    private void layoutCharts() {
        if (chartContent != null && !chartContent.isDisposed()) {
            int width = Math.max(1, chartScroll.getClientArea().width);
            for (ChartCanvas chart : charts) {
                width = Math.max(width, chart.minimumWidth());
            }
            chartScroll.setMinSize(chartContent.computeSize(width, SWT.DEFAULT));
            chartContent.layout(true);
        }
    }

    private static GridData controlData(int widthHint) {
        GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
        data.widthHint = widthHint;
        return data;
    }

    static String label(DBDAttributeBinding attribute) {
        String label = attribute.getLabel();
        return (attribute.getOrdinalPosition() + 1) + ": "
            + (label == null || label.isBlank() ? attribute.getName() : label);
    }

    @Override public boolean isDirty() { return false; }
    @Override public void activatePanel() { refresh(true); }
    @Override public void deactivatePanel() {}
    @Override public boolean needsSeparator() { return false; }
    @Override public void contributeActions(IContributionManager manager) {}
    @Override public void setFocus() {
        if (!charts.isEmpty() && !charts.get(0).isDisposed()) {
            charts.get(0).setFocus();
        }
    }
}
