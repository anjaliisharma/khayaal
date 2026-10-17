import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { apiFetch } from '../api/client';
import type { Recipient } from '../api/types';
import { useSelectedHousehold } from '../hooks/useHousehold';
interface Summary {
  takenOnTime: number;
  takenLate: number;
  skipped: number;
  missed: number;
  onTimePercentage: number;
  missedPercentage: number;
  currentStreak: number;
  mostMissedTimes: string[];
  dailyCompletion: Record<string, number>;
}
export function AdherencePage() {
  const household = useSelectedHousehold();
  const [person, setPerson] = useState('');
  const [range, setRange] = useState(30);
  const people = useQuery({
    queryKey: ['people', household.selected?.id],
    enabled: !!household.selected,
    queryFn: () => apiFetch<Recipient[]>(`/api/households/${household.selected!.id}/people`)
  });
  const selected = person || people.data?.[0]?.id || '';
  const summary = useQuery({
    queryKey: ['adherence', selected, range],
    enabled: !!selected,
    queryFn: () => apiFetch<Summary>(`/api/adherence?person=${selected}&range=${range}`)
  });
  return (
    <div>
      <h1 className="text-3xl font-bold">Adherence</h1>
      <p className="mt-2 text-muted">A summary of recorded events—not medical advice.</p>
      <div className="mt-6 flex flex-wrap gap-3">
        <select
          aria-label="Person"
          className="min-h-12 rounded-xl border bg-surface px-3"
          value={selected}
          onChange={(e) => setPerson(e.target.value)}
        >
          {people.data?.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>
        {[7, 30, 90].map((n) => (
          <button
            key={n}
            className={`min-h-12 rounded-xl border px-4 ${range === n ? 'bg-teal-700 text-white' : ''}`}
            onClick={() => setRange(n)}
          >
            {n} days
          </button>
        ))}
      </div>
      {summary.isLoading && (
        <p role="status" className="mt-8">
          Loading adherence…
        </p>
      )}
      {summary.error && (
        <p role="alert" className="mt-8 text-red-700">
          {summary.error.message}
        </p>
      )}
      {summary.data && (
        <>
          <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {[
              ['On time', summary.data.takenOnTime],
              ['Late', summary.data.takenLate],
              ['Skipped', summary.data.skipped],
              ['Missed', summary.data.missed]
            ].map(([label, value]) => (
              <div key={label} className="rounded-2xl border bg-surface p-5">
                <p className="text-muted">{label}</p>
                <p className="text-3xl font-bold">{value}</p>
              </div>
            ))}
          </div>
          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            <div className="rounded-2xl border bg-surface p-5">
              <p className="text-muted">On-time percentage</p>
              <p className="text-3xl font-bold">{summary.data.onTimePercentage}%</p>
            </div>
            <div className="rounded-2xl border bg-surface p-5">
              <p className="text-muted">Missed percentage</p>
              <p className="text-3xl font-bold">{summary.data.missedPercentage}%</p>
            </div>
          </div>
          <section className="mt-6 rounded-2xl border p-5" aria-label="Daily completion">
            <h2 className="text-xl font-bold">Daily completion</h2>
            <div className="mt-4 flex flex-wrap gap-2">
              {Object.entries(summary.data.dailyCompletion ?? {}).map(([date, value]) => (
                <span
                  key={date}
                  title={`${date}: ${value}%`}
                  className="grid h-11 w-11 place-items-center rounded-lg border text-xs"
                >
                  {value}%
                </span>
              ))}
            </div>
            <p className="mt-4">
              Current streak: <strong>{summary.data.currentStreak} days</strong>
            </p>
            <p className="mt-3">
              Most missed times:{' '}
              <strong>
                {summary.data.mostMissedTimes?.length
                  ? summary.data.mostMissedTimes.join(', ')
                  : 'No missed-time pattern yet'}
              </strong>
            </p>
          </section>
        </>
      )}
    </div>
  );
}
