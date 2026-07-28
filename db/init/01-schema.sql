CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE product (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name        VARCHAR(150) NOT NULL,
  description VARCHAR(500),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE price (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  product_id BIGINT NOT NULL REFERENCES product(id) ON DELETE CASCADE,
  amount     NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
  currency   CHAR(3) NOT NULL DEFAULT 'EUR',
  init_date  DATE NOT NULL,
  end_date   DATE,
  validity   daterange GENERATED ALWAYS AS (
               daterange(init_date,
                         CASE WHEN end_date IS NULL THEN NULL
                              ELSE end_date + 1 END,
                         '[)')
             ) STORED,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT price_dates_ordered
    CHECK (end_date IS NULL OR init_date < end_date),
  CONSTRAINT price_no_overlap
    EXCLUDE USING gist (product_id WITH =, currency WITH =, validity WITH &&)
);

CREATE INDEX idx_price_product_init ON price (product_id, init_date);
