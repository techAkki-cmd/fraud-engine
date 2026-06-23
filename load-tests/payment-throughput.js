import http from 'k6/http';
import { check, fail, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JWT_TOKEN = __ENV.JWT_TOKEN;

if (!JWT_TOKEN) {
  fail('JWT_TOKEN is required for the secured /api/v1/payments endpoint.');
}

export const options = {
  stages: [
    { duration: '30s', target: 500 },
    { duration: '1m', target: 500 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<200'],
    http_req_failed: ['rate<0.01'],
  },
};

function uuidv4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (token) => {
    const random = Math.floor(Math.random() * 16);
    const value = token === 'x' ? random : (random & 0x3) | 0x8;
    return value.toString(16);
  });
}

export default function () {
  const payload = {
    paymentId: uuidv4(),
    accountId: 'acct-mumbai-salary-104392',
    destinationAccountId: 'acct-kirana-settlement-2048',
    merchantId: 'merchant-andheri-kirana-load-test',
    amount: '250.00',
    currency: 'INR',
    paymentMethod: 'UPI',
    occurredAt: new Date().toISOString(),
    riskContext: {
      amountToMedianRatio: '0.8',
      paymentAttemptsLast10Minutes: 1,
      ipGeoDistanceKmFromUsual: '2.5',
      destinationAccountAgeDays: 365,
      destinationPreviouslyFlagged: false,
      deviceFingerprintRisk: 'LOW',
      merchantEstablishedLowRisk: true,
    },
  };
  const correlationId = uuidv4();

  const response = http.post(`${BASE_URL}/api/v1/payments`, JSON.stringify(payload), {
    headers: {
      Authorization: `Bearer ${JWT_TOKEN}`,
      'Content-Type': 'application/json',
      'X-Correlation-ID': correlationId,
    },
    tags: {
      endpoint: 'payments',
    },
  });

  check(response, {
    'payment accepted': (res) => res.status >= 200 && res.status < 300,
  });

  sleep(1);
}
