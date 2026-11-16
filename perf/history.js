import http from 'k6/http';
import { check, sleep } from 'k6';
export const options = {
  stages: [{ duration: '20s', target: 10 }, { duration: '60s', target: 10 }, { duration: '10s', target: 0 }],
  thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<500'] },
};
export default function () {
  const base = __ENV.BASE_URL || 'http://localhost:8080';
  const response = http.get(base + '/api/v1/operations?accountId=11111111-1111-1111-1111-111111111111&size=20');
  check(response, { 'history returned': r => r.status === 200 && r.json().length === 20 });
  sleep(0.1);
}
