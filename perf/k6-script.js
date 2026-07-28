import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SEED_PRODUCTS = 1000;
const SETUP_PRODUCTS = 50;
const PRICES_PER_SETUP_PRODUCT = 10;
const DAY_MS = 24 * 60 * 60 * 1000;

http.setResponseCallback(http.expectedStatuses(200, 201, 404, 409));

const unexpectedResponses = new Rate('unexpected_responses');

export const options = {
  scenarios: {
    main: {
      executor: 'constant-arrival-rate',
      rate: 300,
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 60,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<20'],
    http_req_failed: ['rate<0.01'],
    unexpected_responses: ['rate<0.01'],
  },
};

function isoDate(ms) {
  return new Date(ms).toISOString().slice(0, 10);
}

export function setup() {
  const createProductParams = {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'setup create product' },
  };
  const createPriceParams = {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'setup create price' },
  };
  const productIds = [];
  for (let i = 0; i < SETUP_PRODUCTS; i++) {
    const created = http.post(
      `${BASE_URL}/products`,
      JSON.stringify({ name: `k6 product ${i}`, description: 'created by load test setup' }),
      createProductParams,
    );
    const id = created.json('id');
    productIds.push(id);
    let init = Date.UTC(2025, 0, 1);
    for (let p = 0; p < PRICES_PER_SETUP_PRODUCT; p++) {
      const end = init + 72 * DAY_MS;
      http.post(
        `${BASE_URL}/products/${id}/prices`,
        JSON.stringify({ value: 10 + i + p, initDate: isoDate(init), endDate: isoDate(end) }),
        createPriceParams,
      );
      init = end + DAY_MS;
    }
  }
  const warmupParams = { tags: { name: 'warm-up lookup' } };
  for (const id of productIds) {
    http.get(`${BASE_URL}/products/${id}/prices?date=2025-06-15`, warmupParams);
  }
  for (let seedId = 1; seedId <= SEED_PRODUCTS; seedId++) {
    http.get(`${BASE_URL}/products/${seedId}/prices?date=2024-06-15`, warmupParams);
  }
  return { productIds };
}

const lookupParams = { tags: { name: 'GET price in force' } };
const overlapParams = {
  headers: { 'Content-Type': 'application/json' },
  tags: { name: 'POST overlapping price' },
};
const insertParams = {
  headers: { 'Content-Type': 'application/json' },
  tags: { name: 'POST free-range price' },
};

export default function (data) {
  if (Math.random() < 0.9) {
    const productId = Math.random() < 0.5
      ? 1 + Math.floor(Math.random() * SEED_PRODUCTS)
      : data.productIds[Math.floor(Math.random() * data.productIds.length)];
    const from = Date.UTC(2019, 0, 1);
    const span = Date.UTC(2028, 11, 31) - from;
    const date = isoDate(from + Math.floor(Math.random() * span));
    const res = http.get(`${BASE_URL}/products/${productId}/prices?date=${date}`, lookupParams);
    const ok = check(res, {
      'price lookup answers 200 or 404': (r) => r.status === 200 || r.status === 404,
    });
    unexpectedResponses.add(!ok);
  } else if (Math.random() < 0.05) {
    const productId = data.productIds[Math.floor(Math.random() * data.productIds.length)];
    const res = http.post(
      `${BASE_URL}/products/${productId}/prices`,
      JSON.stringify({ value: 1, initDate: '2025-02-01', endDate: '2025-02-10' }),
      overlapParams,
    );
    const ok = check(res, {
      'deliberate overlap answers 409': (r) => r.status === 409,
    });
    unexpectedResponses.add(!ok);
  } else {
    const productId = data.productIds[Math.floor(Math.random() * data.productIds.length)];
    const offset = (__VU - 1) + (__ITER * 128);
    const init = Date.UTC(2030, 0, 1) + offset * 2 * DAY_MS;
    const res = http.post(
      `${BASE_URL}/products/${productId}/prices`,
      JSON.stringify({ value: 5, initDate: isoDate(init), endDate: isoDate(init + DAY_MS) }),
      insertParams,
    );
    const ok = check(res, {
      'free-range insert answers 201': (r) => r.status === 201,
    });
    unexpectedResponses.add(!ok);
  }
}

export function handleSummary(data) {
  const reqs = data.metrics.http_reqs.values;
  const duration = data.metrics.http_req_duration.values;
  const unexpected = data.metrics.unexpected_responses
    ? data.metrics.unexpected_responses.values.rate
    : 0;
  const thresholdLine = (name) => {
    const metric = data.metrics[name];
    const failed = metric && metric.thresholds
      ? Object.values(metric.thresholds).some((t) => !t.ok)
      : false;
    return `${name}: ${failed ? 'FAILED' : 'passed'}`;
  };
  const lines = [
    '==================== load test summary ====================',
    `total requests      ${reqs.count}`,
    `throughput          ${reqs.rate.toFixed(0)} req/s`,
    `latency avg         ${duration.avg.toFixed(2)} ms`,
    `latency p95         ${duration['p(95)'].toFixed(2)} ms`,
    `latency max         ${duration.max.toFixed(2)} ms`,
    `expected responses  ${((1 - unexpected) * 100).toFixed(2)} %`,
    'thresholds:',
    `  ${thresholdLine('http_req_duration')}`,
    `  ${thresholdLine('http_req_failed')}`,
    `  ${thresholdLine('unexpected_responses')}`,
    '===========================================================',
  ];
  return { stdout: lines.join('\n') + '\n' };
}
