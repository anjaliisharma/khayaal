import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { DoseCard } from './DoseCard';
import type { TodayDose } from '../api/types';
const dose: TodayDose = {
  id: 'dose-1',
  scheduledAt: '2026-10-05T02:30:00Z',
  scheduledLocal: '2026-10-05T08:00:00',
  status: 'PENDING',
  actedBy: null,
  actedByName: null,
  medicineId: 'medicine-1',
  medicineName: 'Synthetic medicine',
  strength: 'user-entered',
  recipientId: 'person-1',
  recipientName: 'Papa'
};
describe('DoseCard', () => {
  it('offers explicit actions and reports the selected action', async () => {
    const action = vi.fn();
    render(<DoseCard dose={dose} onAction={action} />);
    expect(
      screen.getByRole('article', { name: /Synthetic medicine for Papa/ })
    ).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Taken' }));
    expect(action).toHaveBeenCalledWith('taken');
  });
  it.each(['TAKEN', 'SKIPPED'] as const)(
    'offers undo for a %s dose',
    (status: 'TAKEN' | 'SKIPPED') => {
      render(
        <DoseCard
          dose={{ ...dose, status, actedBy: 'user-1', actedByName: 'Synthetic Caregiver' }}
          onAction={vi.fn()}
        />
      );
      expect(screen.getByRole('button', { name: 'Undo' })).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Skip' })).not.toBeInTheDocument();
      expect(screen.getByText('Confirmed by Synthetic Caregiver')).toBeInTheDocument();
    }
  );
});
