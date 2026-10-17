import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ScheduleBuilder, type ScheduleValue } from './ScheduleBuilder';
const schedule: ScheduleValue = {
  type: 'DAILY',
  localTimes: ['08:00'],
  daysOfWeek: [],
  intervalDays: null,
  anchorDate: null
};
describe('ScheduleBuilder', () => {
  it('summarizes the user-entered schedule', () => {
    render(<ScheduleBuilder value={schedule} onChange={vi.fn()} />);
    expect(screen.getByText('Every day at 08:00')).toBeInTheDocument();
  });
  it('adds another time without suggesting a dose', async () => {
    const change = vi.fn();
    render(<ScheduleBuilder value={schedule} onChange={change} />);
    await userEvent.click(screen.getByRole('button', { name: /Add another time/ }));
    expect(change).toHaveBeenCalledWith({ ...schedule, localTimes: ['08:00', '20:00'] });
  });
});
