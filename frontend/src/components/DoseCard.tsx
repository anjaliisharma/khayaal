import { Check, RotateCcw, SkipForward } from 'lucide-react';
import type { TodayDose } from '../api/types';
import { Button } from './ui/button';
export function DoseCard({
  dose,
  busy,
  onAction
}: {
  dose: TodayDose;
  busy?: boolean;
  onAction: (action: 'taken' | 'skip' | 'undo') => void;
}) {
  const time = new Date(dose.scheduledAt).toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit'
  });
  return (
    <article
      className="rounded-2xl border bg-surface p-5 shadow-sm"
      aria-label={`${dose.medicineName} for ${dose.recipientName}`}
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-teal-700">
            {time} · {dose.recipientName}
          </p>
          <h2 className="mt-1 text-xl font-bold">{dose.medicineName}</h2>
          {dose.strength && <p className="text-muted">{dose.strength}</p>}
        </div>
        <span className="rounded-full bg-slate-100 px-3 py-1 text-sm font-semibold dark:bg-slate-800">
          {dose.status}
        </span>
      </div>
      {dose.actedByName && dose.status !== 'PENDING' && dose.status !== 'MISSED' && (
        <p className="mt-3 text-sm text-muted">Confirmed by {dose.actedByName}</p>
      )}
      {dose.status === 'PENDING' || dose.status === 'MISSED' ? (
        <div className="mt-5 grid grid-cols-2 gap-3">
          <Button disabled={busy} onClick={() => onAction('taken')}>
            <Check className="mr-2 h-5 w-5" />
            Taken
          </Button>
          <Button variant="secondary" disabled={busy} onClick={() => onAction('skip')}>
            <SkipForward className="mr-2 h-5 w-5" />
            Skip
          </Button>
        </div>
      ) : dose.status === 'TAKEN' || dose.status === 'SKIPPED' ? (
        <Button variant="ghost" className="mt-4" disabled={busy} onClick={() => onAction('undo')}>
          <RotateCcw className="mr-2 h-4 w-4" />
          Undo
        </Button>
      ) : null}
    </article>
  );
}
