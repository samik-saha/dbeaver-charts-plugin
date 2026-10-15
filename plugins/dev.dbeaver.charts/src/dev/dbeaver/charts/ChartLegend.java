package dev.dbeaver.charts;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

/** Native, accessible series labels, kept outside the plot so a large legend can scroll. */
final class ChartLegend extends Composite {
    ChartLegend(Composite parent) {
        super(parent, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginHeight = 0;
        setLayout(layout);
        setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    void setGroups(List<ChartGroups.Group> groups, int colorOffset, boolean enabled) {
        for (Control child : getChildren()) {
            child.dispose();
        }
        boolean visible = enabled && groups.size() > 1;
        setVisible(visible);
        ((GridData) getLayoutData()).exclude = !visible;
        if (!visible) {
            return;
        }
        for (int i = 0; i < groups.size(); i++) {
            CLabel label = new CLabel(this, SWT.NONE);
            label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            String title = groups.get(i).title();
            label.setText(title.replace("&", "&&"));
            label.setToolTipText(title);
            Image swatch = new Image(getDisplay(), 12, 12);
            GC gc = new GC(swatch);
            Color color = ChartPalette.color(getDisplay(), colorOffset + i);
            try {
                gc.setBackground(color);
                gc.fillRectangle(0, 0, 12, 12);
            } finally {
                gc.dispose();
                color.dispose();
            }
            label.setImage(swatch);
            label.addDisposeListener(event -> swatch.dispose());
        }
    }
}
