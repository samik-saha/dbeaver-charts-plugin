package dev.dbeaver.charts;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.jkiss.dbeaver.model.data.DBDAttributeBinding;

final class DimensionSelectionDialog extends Dialog {
    private final List<DBDAttributeBinding> columns;
    private List<DBDAttributeBinding> selected;
    private CheckboxTableViewer viewer;

    DimensionSelectionDialog(Shell shell, List<DBDAttributeBinding> columns, List<DBDAttributeBinding> selected) {
        super(shell);
        this.columns = columns;
        this.selected = new ArrayList<>(selected);
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Group by dimensions");
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        new Label(area, SWT.WRAP).setText("Select columns. Each unique combination becomes a colored series.");
        viewer = CheckboxTableViewer.newCheckList(area, SWT.BORDER | SWT.V_SCROLL);
        viewer.setContentProvider(ArrayContentProvider.getInstance());
        viewer.setLabelProvider(new LabelProvider() {
            @Override public String getText(Object element) {
                return ChartsPanel.label((DBDAttributeBinding) element);
            }
        });
        GridData grid = new GridData(SWT.FILL, SWT.FILL, true, true);
        grid.widthHint = 420;
        grid.heightHint = 240;
        viewer.getTable().setLayoutData(grid);
        viewer.setInput(columns);
        viewer.setCheckedElements(selected.toArray());
        Composite actions = new Composite(area, SWT.NONE);
        actions.setLayout(new GridLayout(2, false));
        Button all = new Button(actions, SWT.PUSH);
        all.setText("Select all");
        all.addListener(SWT.Selection, event -> viewer.setAllChecked(true));
        Button clear = new Button(actions, SWT.PUSH);
        clear.setText("Clear");
        clear.addListener(SWT.Selection, event -> viewer.setAllChecked(false));
        return area;
    }

    @Override
    protected void okPressed() {
        selected = columns.stream().filter(viewer::getChecked).toList();
        super.okPressed();
    }

    List<DBDAttributeBinding> selected() { return selected; }
}
