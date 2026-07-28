INSERT INTO product (name, description)
SELECT 'Seed product ' || n, 'Seeded catalog entry for load testing'
FROM generate_series(1, 1000) AS n;

WITH timeline AS (
  SELECT p.id AS product_id,
         5 + (p.id % 11)::int AS segments
  FROM product p
  WHERE p.name LIKE 'Seed product %'
)
INSERT INTO price (product_id, amount, currency, init_date, end_date)
SELECT t.product_id,
       (10 + ((t.product_id * 37 + s * 13) % 490))::numeric(12,2),
       'EUR',
       DATE '2020-01-01' + (2557 * s / t.segments),
       DATE '2020-01-01' + (2557 * (s + 1) / t.segments) - 1
FROM timeline t
CROSS JOIN LATERAL generate_series(0, t.segments - 1) AS s;
