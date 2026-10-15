-- SQLite, PostgreSQL, or DuckDB.
-- Chart type: Column or Bar; Category / X: category; Value: amount; Aggregate: SUM.
-- Subscriptions total 1.5M despite the last row appearing after other categories.
SELECT 'Subscriptions' AS category, 'US' AS region, 1250000 AS amount
UNION ALL SELECT 'Ads', 'US', 900000
UNION ALL SELECT 'Refunds', 'US', -150000
UNION ALL SELECT 'No activity', 'US', 0
UNION ALL SELECT 'Unknown', 'US', NULL
UNION ALL SELECT 'Subscriptions', 'US', 250000;
