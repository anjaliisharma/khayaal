import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import { describe, expect, it } from 'vitest';
import { setAccessToken } from '../api/client';
import { server } from '../test/server';
import { TodayPage } from './TodayPage';
describe('TodayPage API integration', () => {
  it('loads and optimistically confirms a dose', async () => {
    setAccessToken('test-token');
    let persistedStatus: 'PENDING' | 'TAKEN' = 'PENDING';
    server.use(
      http.get('http://localhost:8080/api/doses/today', () =>
        Response.json([
          {
            id: 'dose-1',
            scheduledAt: '2026-10-05T02:30:00Z',
            scheduledLocal: '2026-10-05T08:00:00',
            status: persistedStatus,
            actedBy: null,
            actedByName: null,
            medicineId: 'medicine-1',
            medicineName: 'Synthetic medicine',
            strength: null,
            recipientId: 'person-1',
            recipientName: 'Papa'
          }
        ])
      ),
      http.get('http://localhost:8080/api/stock/overview', () => Response.json([])),
      http.post('http://localhost:8080/api/doses/dose-1/taken', () => {
        persistedStatus = 'TAKEN';
        return Response.json({ id: 'dose-1', status: persistedStatus });
      })
    );
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } }
    });
    render(
      <QueryClientProvider client={client}>
        <TodayPage />
      </QueryClientProvider>
    );
    expect(await screen.findByText('Synthetic medicine')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Taken' }));
    expect(await screen.findByText('TAKEN')).toBeInTheDocument();
  });
});
