import http from 'k6/http';
import { check } from 'k6';

export const options={scenarios:{today:{executor:'constant-vus',vus:10,duration:'30s'},stock:{executor:'constant-vus',vus:10,duration:'30s',exec:'stock'}},thresholds:{http_req_duration:['p(95)<100'],http_req_failed:['rate<0.01']}};
const base=__ENV.BASE_URL||'http://localhost:8080';const headers={Authorization:`Bearer ${__ENV.ACCESS_TOKEN}`};
export default function(){check(http.get(`${base}/api/doses/today`,{headers}),{'today 200':r=>r.status===200});}
export function stock(){check(http.get(`${base}/api/stock/overview`,{headers}),{'stock 200':r=>r.status===200});}
