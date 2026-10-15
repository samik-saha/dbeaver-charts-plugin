package dev.dbeaver.charts;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

/**
 * Visual QA harness using the production chart/legend/dialog widgets and synthetic data.
 * macOS --capture mode screenshots each real native window before disposing it.
 */
public final class ChartPreview {
    private record Scenario(String name, ChartOptions.Type type, ChartOptions.BarMode bars,
                            boolean separate, boolean dark, boolean values) {}
    private static final Scenario[] SCENARIOS = {
        new Scenario("combined-lines", ChartOptions.Type.LINE, ChartOptions.BarMode.GROUPED, false, false, false),
        new Scenario("grouped-columns", ChartOptions.Type.COLUMN, ChartOptions.BarMode.GROUPED, false, false, true),
        new Scenario("stacked-columns", ChartOptions.Type.COLUMN, ChartOptions.BarMode.STACKED, false, false, true),
        new Scenario("grouped-bars", ChartOptions.Type.BAR, ChartOptions.BarMode.GROUPED, false, false, true),
        new Scenario("stacked-bars", ChartOptions.Type.BAR, ChartOptions.BarMode.STACKED, false, false, true),
        new Scenario("separate-lines", ChartOptions.Type.LINE, ChartOptions.BarMode.GROUPED, true, false, false),
        new Scenario("dark-lines", ChartOptions.Type.LINE, ChartOptions.BarMode.GROUPED, false, true, false)
    };

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        Display display = new Display();
        try {
            for (Scenario scenario : SCENARIOS) {
                show(display, output, scenario);
            }
            showOptions(display, output);
            System.out.println("Native visual previews captured in " + output);
        } finally {
            display.dispose();
        }
    }

    private static List<ChartGroups.Group> data(ChartOptions options) {
        var builder = new ChartGroups.Builder(List.of("country"), ChartSeries.Aggregation.SUM, 50);
        String[] names = {"US", "CA", "UK"};
        double[][] values = {
            {1_200_000, 1_600_000, 1_400_000, 1_800_000, -200_000, 0},
            {900_000, 1_100_000, Double.NaN, 1_300_000, -150_000, 200_000},
            {500_000, 700_000, 600_000, 800_000, 100_000, 300_000}
        };
        for (int s = 0; s < names.length; s++) {
            for (int i = 0; i < values[s].length; i++) {
                if (Double.isFinite(values[s][i])) {
                    builder.add(List.of(names[s]), LocalDate.of(2026, 9, 11 + i), values[s][i]);
                }
            }
        }
        return builder.build(30, ChartOptions.Order.AUTO, true).groups();
    }

    private static void show(Display display, Path output, Scenario scenario) {
        Shell shell = new Shell(display);
        shell.setText("DBeaver Charts — native visual QA — " + scenario.name);
        shell.setLayout(new GridLayout(1, false));
        Rectangle screen = display.getPrimaryMonitor().getClientArea();
        shell.setBounds(screen.x + 35, screen.y + 35, Math.min(1080, screen.width - 70),
            Math.min(730, screen.height - 70));
        Label title = new Label(shell, SWT.NONE);
        title.setText(scenario.name + "  |  SUM(views) by day and country  |  Synthetic test data");
        title.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        var options = new ChartOptions(scenario.type, ChartOptions.Order.AUTO, scenario.values, true, 70, 30,
            scenario.separate ? ChartOptions.Layout.SEPARATE : ChartOptions.Layout.COMBINED, scenario.bars, true);
        List<ChartGroups.Group> groups = data(options);
        List<ChartCanvas> charts = new ArrayList<>();
        int count = scenario.separate ? groups.size() : 1;
        for (int i = 0; i < count; i++) {
            if (scenario.separate) {
                new Label(shell, SWT.NONE).setText(groups.get(i).title());
            }
            ChartCanvas chart = new ChartCanvas(shell);
            chart.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            List<ChartGroups.Group> shown = scenario.separate ? List.of(groups.get(i)) : groups;
            chart.setGroups(shown, "No data", "SUM(views)", options, scenario.separate ? i : 0);
            charts.add(chart);
            new ChartLegend(shell).setGroups(shown, scenario.separate ? i : 0, true);
        }
        int axisWidth = charts.stream().mapToInt(ChartCanvas::requiredAxisWidth).max().orElse(0);
        charts.forEach(chart -> chart.setAxisLabelWidth(axisWidth));
        Color background = new Color(display, scenario.dark ? 30 : 250, scenario.dark ? 33 : 250, scenario.dark ? 39 : 250);
        Color foreground = new Color(display, scenario.dark ? 235 : 30, scenario.dark ? 235 : 30, scenario.dark ? 235 : 30);
        theme(shell, background, foreground);
        shell.open();
        shell.forceActive();
        display.timerExec(900, () -> {
            try {
                capture(shell, output.resolve(scenario.name + ".png"));
            } catch (Exception exception) {
                exception.printStackTrace();
                display.setData("preview.failure", exception);
            } finally {
                shell.dispose();
            }
        });
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) display.sleep();
        }
        background.dispose();
        foreground.dispose();
        if (display.getData("preview.failure") != null) {
            throw new IllegalStateException("Visual capture failed", (Exception) display.getData("preview.failure"));
        }
    }

    private static void showOptions(Display display, Path output) {
        Shell parent = new Shell(display);
        parent.setBounds(60, 60, 650, 400);
        parent.open();
        ChartOptionsDialog dialog = new ChartOptionsDialog(parent,
            ChartOptions.defaults().withType(ChartOptions.Type.COLUMN));
        display.timerExec(1000, () -> {
            try {
                capture(dialog.getShell(), output.resolve("display-options.png"));
            } catch (Exception exception) {
                display.setData("preview.failure", exception);
            } finally {
                dialog.close();
            }
        });
        dialog.open();
        parent.dispose();
        if (display.getData("preview.failure") != null) {
            throw new IllegalStateException("Options capture failed", (Exception) display.getData("preview.failure"));
        }
    }

    private static void capture(Shell shell, Path target) throws Exception {
        Rectangle bounds = shell.getBounds();
        String region = bounds.x + "," + bounds.y + "," + bounds.width + "," + bounds.height;
        int exit = new ProcessBuilder("/usr/sbin/screencapture", "-x", "-R" + region,
            target.toString()).inheritIO().start().waitFor();
        if (exit != 0) throw new IllegalStateException("screencapture failed: " + exit);
    }

    private static void theme(Control control, Color background, Color foreground) {
        control.setBackground(background);
        control.setForeground(foreground);
        if (control instanceof Composite composite) {
            for (Control child : composite.getChildren()) theme(child, background, foreground);
        }
    }
}
