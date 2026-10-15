-- Runs on SQLite, PostgreSQL, and DuckDB. For other engines, adapt the SQL.
-- SUM combines the two September 2 rows into 200; None shows both rows.
-- Aggregated dates sort chronologically; X labels remain equally spaced.
SELECT '2026-09-01' AS day, 120 AS views
UNION ALL SELECT '2026-09-02', 175
UNION ALL SELECT '2026-09-02', 25
UNION ALL SELECT '2026-09-03', 140
UNION ALL SELECT '2026-09-04', NULL
UNION ALL SELECT '2026-09-05', 210
ORDER BY day;
