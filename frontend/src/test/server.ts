import { http } from 'msw';
import { setupServer } from 'msw/node';
export const server = setupServer(
  http.post('http://localhost:8080/api/auth/refresh', () => new Response(null, { status: 401 }))
);
