import type { ScheduleValue } from './ScheduleBuilder';
export function scheduleSummary(value: ScheduleValue) {
  const times = value.localTimes.filter(Boolean).join(', ') || 'no times';
  if (value.type === 'DAILY') return `Every day at ${times}`;
  if (value.type === 'WEEKDAYS')
    return `${value.daysOfWeek.map((d) => d.slice(0, 3)).join(', ') || 'No days selected'} at ${times}`;
  return `Every ${value.intervalDays ?? 2} days at ${times}`;
}
