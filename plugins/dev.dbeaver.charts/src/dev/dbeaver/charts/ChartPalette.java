package dev.dbeaver.charts;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

final class ChartPalette {
    private static final int[][] COLORS = {
        {0, 114, 178}, {213, 94, 0}, {0, 158, 115}, {176, 93, 155},
        {86, 180, 233}, {173, 139, 0}, {116, 91, 175}, {125, 89, 65},
        {201, 74, 103}, {89, 119, 133}
    };

    static Color color(Display display, int index) {
        if (index >= COLORS.length) {
            return new Color(display, new RGB((float) ((index * 137.508) % 360), 0.65f, 0.78f));
        }
        int[] rgb = COLORS[Math.floorMod(index, COLORS.length)];
        return new Color(display, rgb[0], rgb[1], rgb[2]);
    }
}
