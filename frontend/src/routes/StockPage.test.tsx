import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../test/server';
import { StockPage } from './StockPage';

function renderPage() {
  return render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <StockPage />
    </QueryClientProvider>
  );
}

describe('StockPage', () => {
  it('sorts urgent items first and hides purchase controls from viewers', async () => {
    server.use(
      http.get('http://localhost:8080/api/households', () =>
        Response.json([{ id: 'h1', name: 'Home', defaultTimezone: 'UTC', role: 'VIEWER' }])
      ),
      http.get('http://localhost:8080/api/stock/overview', () =>
        Response.json([
          { medicineId: 'm1', medicineName: 'Well stocked', recipientName: 'A', balance: 20 },
          { medicineId: 'm2', medicineName: 'Out of stock', recipientName: 'B', balance: 0 }
        ])
      )
    );
    renderPage();
    expect(await screen.findByText('Out of stock')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('out of stock');
    expect(screen.queryByRole('button', { name: /Add purchase/ })).not.toBeInTheDocument();
  });

  it('marks a medicine ordered and shows the persisted state', async () => {
    let isOrdered = false;
    server.use(
      http.get('http://localhost:8080/api/households', () =>
        Response.json([{ id: 'h1', name: 'Home', defaultTimezone: 'UTC', role: 'OWNER' }])
      ),
      http.get('http://localhost:8080/api/stock/overview', () =>
        Response.json([
          {
            medicineId: 'm1',
            medicineName: 'Test medicine',
            recipientName: 'A',
            balance: 2,
            forecast: null,
            reorderStatus: isOrdered ? 'ORDERED' : null,
            expectedBy: isOrdered ? '2026-10-08' : null
          }
        ])
      ),
      http.post('http://localhost:8080/api/medicines/m1/reorder-requests', () => {
        isOrdered = true;
        return Response.json(
          { id: 'r1', medicineId: 'm1', expectedBy: '2026-10-08', status: 'ORDERED' },
          { status: 201 }
        );
      })
    );
    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: 'Mark ordered' }));
    expect(await screen.findByText(/Ordered · expected by/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ordered' })).toBeDisabled();
  });
});
