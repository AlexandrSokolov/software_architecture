Reports slow down the live app — where do you move them?
To a dedicated read replica or a Data Warehouse (OLAP). You stream the live data into a separate, column-oriented
database (like Snowflake or ClickHouse) so analytical queries don't compete with live user transactions for CPU,
RAM caches, and disk I/O.


