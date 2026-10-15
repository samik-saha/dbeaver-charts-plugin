package dev.dbeaver.charts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/** Splits rows by an exact tuple of dimension values, then aggregates each group's X values. */
public final class ChartGroups {
    public record Group(String title, ChartSeries.Series series, int rows) {}
    public record Result(List<Group> groups, int totalGroups, int rows) {}
    private record Key(List<Object> values) {}

    public static final class Builder {
        private final List<String> dimensions;
        private final ChartSeries.Aggregation aggregation;
        private final int groupLimit;
        private final Set<Key> seen = new HashSet<>();
        private final Map<Key, PendingGroup> groups = new LinkedHashMap<>();
        private final ChartSeries.Builder domain = new ChartSeries.Builder(ChartSeries.Aggregation.SUM);
        private int rows;

        public Builder(List<String> dimensions, ChartSeries.Aggregation aggregation, int groupLimit) {
            if (groupLimit < 1) {
                throw new IllegalArgumentException("Group limit must be positive");
            }
            this.dimensions = List.copyOf(dimensions);
            this.aggregation = aggregation;
            this.groupLimit = groupLimit;
        }

        public void add(List<Object> values, Object x, Object y) {
            if (values.size() != dimensions.size()) {
                throw new IllegalArgumentException("One value is required per dimension");
            }
            // Copy the tuple; a null dimension is distinct from the literal string "(null)".
            List<Object> keys = new ArrayList<>();
            for (Object value : values) {
                keys.add(ChartSeries.groupKey(value));
            }
            Key key = new Key(Collections.unmodifiableList(keys));
            seen.add(key);
            rows++;
            if (!dimensions.isEmpty() && aggregation != ChartSeries.Aggregation.NONE) {
                domain.add(x, null);
            }
            PendingGroup group = groups.get(key);
            if (group == null && groups.size() < groupLimit) {
                String title = dimensions.isEmpty() ? "All rows"
                    : IntStream.range(0, dimensions.size())
                        .mapToObj(i -> dimensions.get(i) + " = " + display(values.get(i)))
                        .collect(java.util.stream.Collectors.joining("  ·  "));
                group = new PendingGroup(title, new ChartSeries.Builder(aggregation));
                groups.put(key, group);
            }
            // Continue feeding displayed groups after reaching the group limit: totals stay complete.
            if (group != null) {
                group.builder.add(x, y);
                group.rows++;
            }
        }

        public Result build(int pointLimit) {
            return build(pointLimit, ChartOptions.Order.AUTO);
        }

        public Result build(int pointLimit, ChartOptions.Order order) {
            return build(pointLimit, order, false);
        }

        public Result build(int pointLimit, ChartOptions.Order order, boolean combined) {
            List<Group> result = new ArrayList<>();
            if (combined) {
                List<PendingGroup> pending = new ArrayList<>(groups.values());
                List<ChartSeries.Series> series = ChartSeries.combine(
                    pending.stream().map(group -> group.builder).toList(), pointLimit, order);
                for (int i = 0; i < pending.size(); i++) {
                    result.add(new Group(pending.get(i).title, series.get(i), pending.get(i).rows));
                }
                return new Result(List.copyOf(result), seen.size(), rows);
            }
            for (PendingGroup group : groups.values()) {
                ChartSeries.Series series = dimensions.isEmpty() ? group.builder.build(pointLimit, order)
                    : group.builder.buildAligned(domain, pointLimit, order);
                result.add(new Group(group.title, series, group.rows));
            }
            return new Result(List.copyOf(result), seen.size(), rows);
        }
    }

    private static final class PendingGroup {
        final String title;
        final ChartSeries.Builder builder;
        int rows;
        PendingGroup(String title, ChartSeries.Builder builder) {
            this.title = title;
            this.builder = builder;
        }
    }

    private static String display(Object value) {
        if (value == null) {
            return "(null)";
        }
        String text = String.valueOf(value).replace("\n", " ").replace("\r", " ").replace("\t", " ");
        return value instanceof String ? "\"" + text.replace("\"", "\\\"") + "\"" : text;
    }
}
