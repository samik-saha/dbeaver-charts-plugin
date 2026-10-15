package dev.dbeaver.charts;

import java.util.Arrays;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

final class ChartOptionsDialog extends Dialog {
    private ChartOptions options;
    private Combo order;
    private Button values;
    private Button grid;
    private Spinner width;
    private Spinner limit;
    private Combo barMode;
    private Button legend;

    ChartOptionsDialog(Shell shell, ChartOptions options) {
        super(shell);
        this.options = options;
    }

    @Override protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Chart display options");
    }

    @Override protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        Composite form = new Composite(area, SWT.NONE);
        form.setLayout(new GridLayout(2, false));
        form.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        new Label(form, SWT.NONE).setText("Order");
        order = new Combo(form, SWT.READ_ONLY);
        order.setItems(Arrays.stream(ChartOptions.Order.values()).map(ChartOptions.Order::label).toArray(String[]::new));
        order.select(options.order().ordinal());
        values = new Button(form, SWT.CHECK);
        values.setText("Show values");
        values.setSelection(options.showValues());
        values.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        grid = new Button(form, SWT.CHECK);
        grid.setText("Show grid lines");
        grid.setSelection(options.showGrid());
        grid.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        legend = new Button(form, SWT.CHECK);
        legend.setText("Show series legend");
        legend.setSelection(options.showLegend());
        legend.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        new Label(form, SWT.NONE).setText("Bar arrangement");
        barMode = new Combo(form, SWT.READ_ONLY);
        barMode.setItems(Arrays.stream(ChartOptions.BarMode.values()).map(ChartOptions.BarMode::label).toArray(String[]::new));
        barMode.select(options.barMode().ordinal());
        new Label(form, SWT.NONE).setText("Bar width (%)");
        width = new Spinner(form, SWT.BORDER);
        width.setMinimum(20);
        width.setMaximum(100);
        width.setSelection(options.barWidth());
        new Label(form, SWT.NONE).setText("Maximum categories");
        limit = new Spinner(form, SWT.BORDER);
        limit.setMinimum(1);
        limit.setMaximum(200);
        limit.setSelection(options.barLimit());
        boolean bars = options.type() != ChartOptions.Type.LINE;
        width.setEnabled(bars);
        limit.setEnabled(bars);
        barMode.setEnabled(bars);
        Label note = new Label(form, SWT.WRAP);
        note.setText("Bar settings apply to Column and Bar charts.\n"
            + "Combined charts rank by the total across displayed series.\n"
            + "Separate charts rank within each dimension group.\n"
            + "Bars always start at zero. Hover shows full values.");
        note.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        return area;
    }

    @Override protected void okPressed() {
        options = new ChartOptions(options.type(), ChartOptions.Order.values()[order.getSelectionIndex()],
            values.getSelection(), grid.getSelection(), width.getSelection(), limit.getSelection(),
            options.layout(), ChartOptions.BarMode.values()[barMode.getSelectionIndex()], legend.getSelection());
        super.okPressed();
    }

    ChartOptions options() { return options; }
}
