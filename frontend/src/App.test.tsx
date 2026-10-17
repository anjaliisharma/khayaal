import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AuthProvider } from './auth/AuthProvider';
import { App } from './App';
describe('authentication entry points', () => {
  it('shows registration and the safety disclaimer', async () => {
    render(
      <MemoryRouter initialEntries={['/register']}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>
    );
    expect(
      await screen.findByRole('heading', { name: 'Create your Khayaal account' })
    ).toBeInTheDocument();
    expect(screen.getByText(/It is not medical advice/)).toBeInTheDocument();
  });
});
