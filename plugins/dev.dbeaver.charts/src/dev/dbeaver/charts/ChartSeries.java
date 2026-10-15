package dev.dbeaver.charts;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Aggregation and label preparation, independent of DBeaver and SWT. */
public final class ChartSeries {
    public enum Aggregation {
        SUM("SUM"), AVG("AVG"), MIN("MIN"), MAX("MAX"), NONE("None");

        private final String label;
        Aggregation(String label) { this.label = label; }
        public String label() { return label; }
    }

    public record Series(double[] values, String[] labels, String[] details,
                         int totalPoints, int ignoredValues, boolean chronological) {}

    private record X(Object key, LocalDateTime date, String detail) {}
    private record Entry(X x, Accumulator values) {}
    private record Position(Object key, int occurrence) {}
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault());
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.getDefault());

    /** Align before sorting/limiting so every overlaid series uses exactly the same categories. */
    static List<Series> combine(List<Builder> builders, int limit, ChartOptions.Order order) {
        List<Map<Position, Entry>> indexed = new ArrayList<>();
        Map<Position, Entry> domain = new LinkedHashMap<>();
        Map<Entry, Position> positions = new java.util.IdentityHashMap<>();
        for (Builder builder : builders) {
            Map<Position, Entry> own = new LinkedHashMap<>();
            Map<Object, Integer> occurrences = new java.util.HashMap<>();
            for (Entry entry : builder.orderedEntries()) {
                int occurrence = builder.aggregation == Aggregation.NONE
                    ? occurrences.merge(entry.x.key, 1, Integer::sum) - 1 : 0;
                Position key = new Position(entry.x.key, occurrence);
                own.put(key, entry);
                Entry total = domain.computeIfAbsent(key, unused -> {
                    Entry created = new Entry(entry.x, new Accumulator());
                    positions.put(created, key);
                    return created;
                });
                double value = entry.values.value(builder.aggregation);
                if (Double.isFinite(value)) {
                    total.values.add(BigDecimal.valueOf(value));
                }
            }
            indexed.add(own);
        }
        List<Entry> categories = new ArrayList<>(domain.values());
        if (!builders.isEmpty() && builders.get(0).aggregation != Aggregation.NONE && Builder.chronological(categories)) {
            categories.sort(Comparator.comparing(e -> e.x.date, Comparator.nullsLast(Comparator.naturalOrder())));
        }
        categories = new Builder(Aggregation.SUM).order(categories, order);
        List<Series> result = new ArrayList<>();
        for (int i = 0; i < builders.size(); i++) {
            List<Entry> aligned = new ArrayList<>();
            for (Entry category : categories) {
                Entry own = indexed.get(i).get(positions.get(category));
                aligned.add(new Entry(category.x, own == null ? new Accumulator() : own.values));
            }
            result.add(builders.get(i).buildEntries(aligned, limit, order));
        }
        return result;
    }

    public static final class Builder {
        private final Aggregation aggregation;
        private final Map<Object, Entry> groups = new LinkedHashMap<>();
        private final List<Entry> individual = new ArrayList<>();
        private List<Entry> orderedCache;
        private int ignoredValues;

        public Builder(Aggregation aggregation) { this.aggregation = aggregation; }

        public void add(Object xValue, Object yValue) {
            orderedCache = null;
            X x = xValue(xValue);
            Entry entry;
            if (aggregation == Aggregation.NONE) {
                entry = new Entry(x, new Accumulator());
                individual.add(entry);
            } else {
                entry = groups.computeIfAbsent(x.key(), key -> new Entry(x, new Accumulator()));
            }
            BigDecimal value = decimal(yValue);
            if (value == null) {
                ignoredValues++;
            } else {
                entry.values.add(value);
            }
        }

        public Series build(int limit) {
            return build(limit, ChartOptions.Order.AUTO);
        }

        public Series build(int limit, ChartOptions.Order order) {
            return buildEntries(order(orderedEntries(), order), limit, order);
        }

        /** Use the same X domain across grouped charts, leaving gaps for absent X values. */
        public Series buildAligned(Builder domain, int limit) {
            return buildAligned(domain, limit, ChartOptions.Order.AUTO);
        }

        public Series buildAligned(Builder domain, int limit, ChartOptions.Order order) {
            if (aggregation == Aggregation.NONE) {
                return build(limit, order);
            }
            List<Entry> aligned = new ArrayList<>();
            for (Entry entry : domain.orderedEntries()) {
                Entry own = groups.get(entry.x.key);
                aligned.add(new Entry(entry.x, own == null ? new Accumulator() : own.values));
            }
            return buildEntries(order(aligned, order), limit, order);
        }

        private List<Entry> order(List<Entry> source, ChartOptions.Order order) {
            if (order == ChartOptions.Order.AUTO) {
                return source;
            }
            List<Entry> sorted = new ArrayList<>(source);
            Map<Entry, Double> values = new java.util.IdentityHashMap<>();
            if (order.byValue()) {
                for (Entry entry : source) {
                    values.put(entry, entry.values.value(aggregation));
                }
            }
            sorted.sort((a, b) -> {
                if (order.byValue()) {
                    double av = values.get(a);
                    double bv = values.get(b);
                    if (!Double.isFinite(av) || !Double.isFinite(bv)) {
                        return Boolean.compare(!Double.isFinite(av), !Double.isFinite(bv));
                    }
                    return order == ChartOptions.Order.VALUE_DESC ? Double.compare(bv, av) : Double.compare(av, bv);
                }
                if (a.x.key == null || b.x.key == null) {
                    return Boolean.compare(a.x.key == null, b.x.key == null);
                }
                int comparison;
                if (a.x.date != null && b.x.date != null) {
                    comparison = a.x.date.compareTo(b.x.date);
                } else if (a.x.key instanceof BigDecimal av && b.x.key instanceof BigDecimal bv) {
                    comparison = av.compareTo(bv);
                } else {
                    int aKind = a.x.date != null ? 0 : a.x.key instanceof BigDecimal ? 1 : 2;
                    int bKind = b.x.date != null ? 0 : b.x.key instanceof BigDecimal ? 1 : 2;
                    comparison = Integer.compare(aKind, bKind);
                    if (comparison == 0) {
                        comparison = a.x.detail.compareToIgnoreCase(b.x.detail);
                        if (comparison == 0) {
                            comparison = a.x.detail.compareTo(b.x.detail);
                        }
                    }
                }
                return order == ChartOptions.Order.CATEGORY_DESC ? -comparison : comparison;
            });
            return sorted;
        }

        private List<Entry> orderedEntries() {
            if (orderedCache != null) {
                return orderedCache;
            }
            List<Entry> entries = new ArrayList<>(aggregation == Aggregation.NONE ? individual : groups.values());
            if (chronological(entries) && aggregation != Aggregation.NONE) {
                entries.sort(Comparator.comparing(e -> e.x.date, Comparator.nullsLast(Comparator.naturalOrder())));
            }
            orderedCache = List.copyOf(entries);
            return orderedCache;
        }

        private static boolean chronological(List<Entry> entries) {
            return entries.stream().anyMatch(e -> e.x.date != null)
                && entries.stream().allMatch(e -> e.x.date != null || e.x.key == null);
        }

        private Series buildEntries(List<Entry> entries, int limit, ChartOptions.Order order) {
            if (limit < 1) {
                throw new IllegalArgumentException("Point limit must be positive");
            }
            boolean chronological = chronological(entries);
            boolean hasTime = entries.stream().anyMatch(e -> e.x.date != null
                && !e.x.date.toLocalTime().equals(java.time.LocalTime.MIDNIGHT));
            int size = Math.min(limit, entries.size());
            double[] values = new double[size];
            String[] labels = new String[size];
            String[] details = new String[size];
            for (int i = 0; i < size; i++) {
                Entry entry = entries.get(i);
                values[i] = entry.values.value(aggregation);
                X x = entry.x;
                labels[i] = x.date == null ? shorten(x.detail)
                    : hasTime ? MONTH_DAY.format(x.date) + "\n" + TIME.format(x.date)
                    : DAY.format(x.date) + "\n" + x.date.getYear();
                details[i] = x.detail;
            }
            return new Series(values, labels, details, entries.size(), ignoredValues,
                chronological && aggregation != Aggregation.NONE
                    && (order == ChartOptions.Order.AUTO || order == ChartOptions.Order.CATEGORY_ASC));
        }
    }

    static Object groupKey(Object value) {
        if (value instanceof Number) {
            BigDecimal numeric = decimal(value);
            return numeric == null ? value : numeric.stripTrailingZeros();
        }
        return value;
    }

    private static final class Accumulator {
        private BigDecimal sum = BigDecimal.ZERO;
        private BigDecimal min;
        private BigDecimal max;
        private long count;

        void add(BigDecimal value) {
            sum = sum.add(value);
            min = min == null ? value : min.min(value);
            max = max == null ? value : max.max(value);
            count++;
        }

        double value(Aggregation aggregation) {
            if (count == 0) {
                return Double.NaN;
            }
            BigDecimal value = switch (aggregation) {
                case AVG -> sum.divide(BigDecimal.valueOf(count), MathContext.DECIMAL128);
                case MIN -> min;
                case MAX -> max;
                default -> sum;
            };
            double result = value.doubleValue();
            return Double.isFinite(result) ? result : Double.NaN;
        }
    }

    private static BigDecimal decimal(Object value) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            return null;
        }
        try {
            return value instanceof BigDecimal decimal ? decimal : new BigDecimal(number.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static X xValue(Object value) {
        if (value == null) {
            return new X(null, null, "(null)");
        }
        LocalDateTime date = null;
        String detail = String.valueOf(value);
        // Handle SQL types before java.util.Date: SQL Date does not implement toInstant().
        if (value instanceof java.sql.Timestamp timestamp) {
            date = timestamp.toLocalDateTime();
        } else if (value instanceof java.sql.Date sqlDate) {
            date = sqlDate.toLocalDate().atStartOfDay();
        } else if (value instanceof LocalDate localDate) {
            date = localDate.atStartOfDay();
        } else if (value instanceof LocalDateTime localDateTime) {
            date = localDateTime;
        } else {
            Instant instant = value instanceof Instant i ? i
                : value instanceof OffsetDateTime offset ? offset.toInstant()
                : value instanceof ZonedDateTime zoned ? zoned.toInstant()
                : value instanceof java.util.Date util && !(value instanceof java.sql.Time) ? util.toInstant()
                : null;
            if (instant != null) {
                date = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
                detail = instant + " (UTC)";
            } else if (value instanceof String text) {
                date = parseDate(text);
            }
        }
        // Keep display formatting out of identity: distinct timestamps never merge just because labels match.
        return new X(groupKey(value), date, detail);
    }

    private static LocalDateTime parseDate(String text) {
        try {
            return LocalDate.parse(text).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(text.replace(' ', 'T'));
            } catch (DateTimeParseException alsoIgnored) {
                return null;
            }
        }
    }

    private static String shorten(String value) {
        String singleLine = value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        return singleLine.length() > 80 ? singleLine.substring(0, 80) + "…" : singleLine;
    }
}
