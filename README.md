# DBeaver Charts

A Java/Eclipse plugin that adds a **Charts** panel beside DBeaver query results. Choose a line, vertical column, or horizontal bar chart. Dimension groups render together as colored series by default, with separate charts available as an alternative. All types share numeric aggregation, date labels, and automatic K/M/B units.

## Requirements

- DBeaver Community 26.1.4 or 26.2.0 as the initial development targets.
- JDK 21 or newer (`java`, `javac`, and `jar` on `PATH`, or `JAVA_HOME` set to a JDK).
- Python 3.10 or newer for the dependency-free build script.

The plugin contains no platform-specific libraries. It uses DBeaver's own SWT, including Cocoa on Apple Silicon and Intel Macs, Win32 on Windows, and GTK on Linux. Compilation is checked against the installed Mac versions; runtime behavior on each OS still needs the smoke test below.

## Build

On macOS:

```sh
python3 scripts/build.py --dbeaver /Applications/DBeaver.app
```

On Windows:

```powershell
py scripts/build.py --dbeaver "C:\Program Files\DBeaver"
```

On Linux:

```sh
python3 scripts/build.py --dbeaver /usr/share/dbeaver
```

You can also set `DBEAVER_HOME`. The script compiles for Java 21 against your installed DBeaver bundles, runs the chart conversion/scaling checks, and produces:

- `build/dev.dbeaver.charts_0.5.1.jar`
- `build/dbeaver-charts-0.5.1-dropins.zip`

No Maven, downloaded dependencies, or database connection is needed. Building against the exact host installation keeps this starter small and makes DBeaver API changes visible at compile time. A pinned target platform and Tycho/p2 release pipeline can be added when distributing releases.

## Install from a release

End users should install from a release rather than building from source.

1. Download `dbeaver-charts-<version>-update-site.zip` from the [releases page](https://github.com/samik-saha/dbeaver-charts-plugin/releases). Do not unzip it.
2. In DBeaver, open **Help → Install New Software…**, click **Add… → Archive…**, and select the downloaded zip.
3. Check **DBeaver Charts**, click **Next** through the wizard, accept the license, and click **Finish**.
4. Restart DBeaver when prompted.
5. Run a query with at least one numeric column. In the result grid's **Panels** menu, enable **Charts**.

### Update to a newer release

You do not need to uninstall first. Each release increases the plugin version, and DBeaver's installer replaces the old version in place:

1. Download the new `dbeaver-charts-<version>-update-site.zip` from the [releases page](https://github.com/samik-saha/dbeaver-charts-plugin/releases).
2. Repeat steps 2–4 above with the new zip. The installer detects the higher version of the installed feature and performs an update.
3. Restart DBeaver when prompted.

Two things to know:

- **Help → Check for Updates** does not find release zips automatically, because it only polls remote update sites at fixed addresses. Always point **Install New Software** at the new zip once per release.
- A rebuilt plugin with the *same* version number will not update an existing installation (the installer reports it as already installed). That is why every release bumps the version — `0.5.1` updates `0.5.0`, for example.

### Uninstall or downgrade

Downgrading to an older version requires uninstalling first. Open **DBeaver → About DBeaver → Installation Details → Installed Software**, select **DBeaver Charts**, click **Uninstall…**, and restart DBeaver. The same steps uninstall the plugin entirely.

## Install for development

Use a **separate development copy of DBeaver** and quit that copy before installing.

1. Extract the drop-in ZIP into its Eclipse installation directory. On macOS, this is `DBeaver.app/Contents/Eclipse`; on Windows/Linux it is the directory containing `plugins/`.
2. Check that the resulting path is `dropins/dbeaver-charts/eclipse/plugins/dev.dbeaver.charts_0.5.1.jar`.
3. Enable the drop-in loader in the development copy. DBeaver 26.1.4 includes the loader but does not automatically start it:

   ```sh
   python3 scripts/enable-dropins.py --dbeaver "/path/to/Dev DBeaver.app"
   ```

   This preserves `configuration/config.ini.before-charts` as a backup and adds the loader to the startup bundles. Run it once per development installation. Windows/Linux use the same script with their installation directory.

4. Start that DBeaver copy with `-clean` once. For example on macOS:

   ```sh
   "/path/to/Dev DBeaver.app/Contents/MacOS/dbeaver" -clean -data "$HOME/dbeaver-charts-workspace"
   ```

5. Check **DBeaver → About DBeaver → Installation Details → Plug-ins** for `dev.dbeaver.charts`.
6. Run a query with at least one numeric column. In the result grid's **Panels** menu, enable **Charts** (previously called Line chart).
7. Choose a **Chart type**, **Value**, and **Category / X** column.
8. Click **Group by**, check one or more columns, and click **OK**. Each distinct combination becomes a colored series in the same chart, with a matching legend. Use **Clear → OK** to return to one series.
9. Choose **Series view → Separate charts** for one chart per group, or **Combined chart** to put them together again.
10. Click **Display options…** to choose **Side by side** or **Stacked** bars, legend visibility, ordering, value labels, grid lines, bar width, and the maximum number of bar categories.

The archive is a development drop-in, not a p2 update-site archive for “Install New Software.” Editing an app bundle can affect macOS signature validation; use a development copy you can replace. This build does not install into or launch your existing DBeaver.

To update after building:

```sh
python3 scripts/install-dev.py --dbeaver "$HOME/DBeaver.app"
```

The installer backs up the previous plugin under `build/install-backups/`, installs the current build, and removes older versions from this plugin's drop-in folder. Save your work, quit the development copy, and restart it with `-clean` to load the update. To uninstall, remove the `dropins/dbeaver-charts` folder and restart with `-clean`.

## Chart types and options

| Setting | Behavior |
| --- | --- |
| Line | Connected points with gaps for missing values; grouped output appears as sparklines. |
| Column (vertical) | Categories across the bottom, with vertical bars extending from zero. |
| Bar (horizontal) | Categories down the left, with horizontal bars extending from zero. |
| Series view | Combined chart by default: colored lines or bars with one shared numeric scale. Separate charts use one plot per dimension combination, with independent scales. |
| Bar arrangement | Side by side by default; Stacked places the series in the same category slot. Applies to both bar orientations. |
| Show series legend | Show/hide the colored series names below a combined chart. Enabled by default. |
| Order | Automatic/result order, category ascending/descending, or value ascending/descending. |
| Show values | Compact labels near points or bar ends; crowded labels are suppressed to avoid overlap. Full values remain available on hover. |
| Show grid lines | Toggle numeric-axis grid lines; enabled by default. |
| Bar width | 20–100% of each category slot, default 70%. Applies to both bar orientations. |
| Maximum categories | 1–200 per bar/column chart, default 30. Applied **after aggregation and ordering**. |

Bars always include zero in their numeric scale. Negative columns extend down, negative horizontal bars extend left, and missing values have no segment. Positive and negative stacked values accumulate separately on opposite sides of zero; the axis includes the full stack totals. Hover identifies an individual segment and its value, not the cumulative stack endpoint. Scrolling provides space for additional categories and large legends.

In combined mode, value ordering ranks categories by the **net total across displayed series**, after each series has been aggregated. All series use that same order and category limit. In separate mode, value ordering ranks within each dimension group, so charts may have different category orders. Chart type and display choices last for the current panel instance; they are not saved as a query preset.

## Data behavior

- One numeric Y column and a visible X column. The initial X selection prefers a date column, then a nonnumeric column.
- **Group by** accepts multiple columns through a checkbox dialog. For example, selecting `country` and `device` creates series for `(US, TV)`, `(US, Phone)`, `(CA, TV)`, etc. Series and legend entries appear in first-seen order. Separate charts retain the same series colors.
- Combined charts align all series by category before sorting or truncation; absent values leave gaps instead of shifting remaining points. With aggregation None, repeated X values align by occurrence (first with first, second with second), preserving raw values without silently aggregating. Null dimension/category values remain separate from the literal text `"(null)"`.
- **SUM is the default:** rows with equal category values are combined. AVG, MIN, MAX, and None are also available. Choosing Row number gives each row a unique category.
- Grouping uses the underlying X values, not formatted labels. Timestamps remain distinct at their original precision; there is no implicit day/month bucketing.
- Positions are **equally spaced categories**, not a continuous time/numeric scale. Automatic ordering sorts aggregated dates chronologically, with null last; other categories retain first-seen order. Use None with Automatic order and SQL `ORDER BY` to preserve an explicit row order.
- Dates use two-line labels on line/column charts and single-line labels beside horizontal bars; timestamps include the time. Tick density adapts to the panel width. Hover over the plot for the full category and aggregated value. Java/SQL date types and ISO local date/timestamp strings are recognized. Timezone-aware Java values display in UTC; hover text identifies the timezone.
- Only already loaded result rows are read. The chart does not execute SQL or fetch extra rows.
- Aggregations ignore nulls, nonnumeric values, NaN, and infinity. An all-invalid group has no numeric plot. None leaves gaps at invalid rows. A constant line series is centered vertically; bars keep their zero baseline.
- All loaded rows contribute to aggregation before category limits are applied (10,000 positions for lines, the chosen limit for bars/columns). Up to the first 50 groups are displayed, with shown/total group counts reported explicitly. Later rows still contribute to displayed groups, so limits never produce partial sums. The status line also reports row and point counts, ignored values, ordering, truncation, and gaps.
- DBeaver's panel refresh callbacks update the chart. **Refresh** redraws from the current loaded rows.
- The panel icon is 16 pixels, matching DBeaver's standard icons, with a 32-pixel Retina variant.
- Numeric-axis labels automatically share a K, M, or B unit based on the largest absolute value (for example, `1.2K`, `1.25M`, `2.5B`). Small ranges keep ordinary numbers; close bounds retain enough precision to stay distinct. Hover values remain unabridged.
- Zooming, export, percentage stacking, and database writes are not implemented.

## Project layout

```text
plugins/dev.dbeaver.charts/
  META-INF/MANIFEST.MF       OSGi bundle identity and host dependencies
  plugin.xml                DBeaver result-panel extension
  src/dev/dbeaver/charts/
    ChartsPanel.java        Type selection, controls, and result-set adapter
    ChartCanvas.java        Shared axes, hover, and line/column/bar rendering
    ChartOptions.java       Chart types and display settings
    ChartOptionsDialog.java  Display-options UI
    ChartGeometry.java      Zero baselines, bar bounds, and hit testing
    ChartScene.java         Shared numeric scale and signed stack endpoints
    ChartLegend.java        Accessible native series labels and swatches
    ChartPalette.java       Series colors shared across chart layouts
    ChartValues.java        Numeric conversion and scaling
    ChartSeries.java        Aggregation, grouping, and date labels
    ChartGroups.java        Dimension tuples and shared X positions
    DimensionSelectionDialog.java  Multi-column checkbox selection
scripts/build.py            Portable local build, checks, and packaging
scripts/install-dev.py      Backed-up installation into a development copy
tests/                      Dependency-free numerical checks
examples/line-chart.sql      Example query
```

For Eclipse development, import the plugin using **Existing Projects into Workspace** in Eclipse PDE. Set a target platform based on the DBeaver installation and use JavaSE-21. The checked-in `.project`, `.classpath`, and `build.properties` describe a standard Eclipse plugin project.

The panel retains its original internal extension ID, `dev.dbeaver.charts.line`, so existing panel visibility preferences survive the rename to Charts.

## Manual smoke test

1. Run `examples/line-chart.sql` in a SQLite, PostgreSQL, or DuckDB connection.
2. Open **Charts**, choose Line, `views` for Value and `day` for Category / X. Verify SUM is selected, duplicate days combine, dates sort, and the all-null day leaves a gap.
3. Resize the panel and try light/dark themes. Check labels on a Retina display.
4. Rerun with a different numeric column, fetch more rows, and click Refresh. Check for stale selections.
5. Try no rows, one row, a constant series, negative values, and an all-null numeric column.
6. Close and reopen the result tab; check DBeaver's Error Log for plugin errors.
7. Switch between SUM, AVG, MIN, MAX, and None. Check that None restores individual rows.
8. Plot timestamps on the same day with distinct fractional seconds. Check they remain separate points and hover text retains full precision.
9. Run `examples/grouped-sparklines.sql`, choose `day` for X and `views` for Y, then group by `country` and `device`. Check for three colored lines and a matching legend, a US/TV total of 150 on September 15, and a gap for US/Phone on September 16.
10. Switch Series view to Separate charts and back. Resize and scroll the panel, then clear dimensions. Check that one series returns and previously selected dimensions survive a refresh with the same columns.
11. Run `examples/bar-chart.sql`, select `category` and `amount`, and switch between Column and Bar. Check positive, negative, zero, and missing values, along with hover values.
12. In Display options, choose Value descending and a category limit of 2. Verify Subscriptions (1.5M) and Ads (900K) are selected, even though one subscription row occurs at the end of the result.
13. Toggle grid/value labels, change bar width, and group by `region`. Check that each group keeps its own aggregates. Switch back to Line and check that category limits specific to bars no longer apply.
14. With multiple groups, test Side by side and Stacked in both orientations. Verify shared ordering, distinct colors, legends, individual segment tooltips, and positive/negative stack totals.

These GUI checks are separate from the build's automated numerical checks. Repeat them on Windows and Linux before claiming runtime support there.

## Native visual QA on macOS

```sh
python3 scripts/build.py --dbeaver "$HOME/DBeaver.app"
python3 scripts/preview.py --dbeaver "$HOME/DBeaver.app"
```

The preview opens temporary native windows using the production chart, legend, and options dialog with synthetic data. It captures combined lines, grouped/stacked columns and bars, separate charts, dark rendering, and display options into `build/visual-qa/`, then closes the windows. Screen capture permission is required. This checks the real SWT renderer; it does not substitute for an end-to-end DBeaver query/editor test.
