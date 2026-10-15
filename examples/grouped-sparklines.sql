-- SQLite, PostgreSQL, or DuckDB.
-- X: day; Y: views; Aggregate: SUM; Group by: country, device.
-- US/TV: 150, 180, 200. US/Phone: 25, gap, 40. CA/TV: 80, 90, 100.
SELECT '2026-09-15' AS day, 'US' AS country, 'TV' AS device, 100 AS views
UNION ALL SELECT '2026-09-15', 'US', 'TV', 50
UNION ALL SELECT '2026-09-16', 'US', 'TV', 180
UNION ALL SELECT '2026-09-17', 'US', 'TV', 200
UNION ALL SELECT '2026-09-15', 'US', 'Phone', 25
UNION ALL SELECT '2026-09-17', 'US', 'Phone', 40
UNION ALL SELECT '2026-09-15', 'CA', 'TV', 80
UNION ALL SELECT '2026-09-16', 'CA', 'TV', 90
UNION ALL SELECT '2026-09-17', 'CA', 'TV', 100
ORDER BY country DESC, device DESC, day;
