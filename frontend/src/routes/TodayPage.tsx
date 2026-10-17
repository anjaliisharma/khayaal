import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { apiFetch } from '../api/client';
import type { TodayDose } from '../api/types';
import { DoseCard } from '../components/DoseCard';
export function TodayPage() {
  const queryClient = useQueryClient();
  const [person, setPerson] = useState('all');
  const query = useQuery({
    queryKey: ['today'],
    queryFn: () => apiFetch<TodayDose[]>('/api/doses/today')
  });
  const stock = useQuery({
    queryKey: ['stock'],
    queryFn: () =>
      apiFetch<
        Array<{
          medicineId: string;
          medicineName: string;
          balance: number;
          forecast: { daysLeft: number; reorderBy: string | null } | null;
          reorderStatus: string | null;
        }>
      >('/api/stock/overview')
  });
  const action = useMutation({
    mutationFn: ({ dose, kind }: { dose: TodayDose; kind: 'taken' | 'skip' | 'undo' }) =>
      apiFetch<void>(`/api/doses/${dose.id}/${kind}`, {
        method: 'POST',
        headers: { 'Idempotency-Key': crypto.randomUUID() },
        body: kind === 'undo' ? undefined : '{}'
      }),
    onMutate: async ({ dose, kind }) => {
      await queryClient.cancelQueries({ queryKey: ['today'] });
      const previous = queryClient.getQueryData<TodayDose[]>(['today']);
      queryClient.setQueryData<TodayDose[]>(['today'], (old) =>
        old?.map((item) =>
          item.id === dose.id
            ? {
                ...item,
                status: kind === 'taken' ? 'TAKEN' : kind === 'skip' ? 'SKIPPED' : 'PENDING'
              }
            : item
        )
      );
      return { previous };
    },
    onError: (_error, _variables, context) =>
      queryClient.setQueryData(['today'], context?.previous),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['today'] })
  });
  const people = useMemo(
    () => Array.from(new Map(query.data?.map((d) => [d.recipientId, d.recipientName])).entries()),
    [query.data]
  );
  const doses = query.data?.filter((d) => person === 'all' || d.recipientId === person) ?? [];
  const urgentStock =
    stock.data?.filter(
      (item) =>
        item.reorderStatus !== 'ORDERED' &&
        (item.balance <= 0 || (item.forecast?.daysLeft ?? Number.MAX_SAFE_INTEGER) <= 3)
    ) ?? [];
  return (
    <div>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-sm font-semibold uppercase tracking-wide text-teal-700">
            Daily routine
          </p>
          <h1 className="text-3xl font-bold">Today</h1>
          <p className="mt-2 text-muted">
            Confirm only what actually happened. Khayaal does not advise doses.
          </p>
        </div>
        {people.length > 1 && (
          <label className="font-medium">
            Person
            <select
              className="ml-2 min-h-12 rounded-xl border bg-surface px-3"
              value={person}
              onChange={(e) => setPerson(e.target.value)}
            >
              <option value="all">Everyone</option>
              {people.map(([id, name]) => (
                <option key={id} value={id}>
                  {name}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>
      {!!urgentStock.length && (
        <Link
          to="/stock"
          className="mt-6 block rounded-2xl border border-amber-400 bg-amber-50 p-4 font-semibold text-amber-950"
        >
          {urgentStock.some((item) => item.balance <= 0)
            ? 'Critical: one or more medicines are out of stock.'
            : `${urgentStock.length} medicine${urgentStock.length === 1 ? '' : 's'} may need reordering soon.`}{' '}
          Review stock →
        </Link>
      )}
      {query.isLoading && (
        <p role="status" className="py-12">
          Loading today’s doses…
        </p>
      )}
      {query.error && (
        <p role="alert" className="mt-8 text-red-700">
          {query.error.message}
        </p>
      )}
      {!query.isLoading && !query.error && doses.length === 0 && (
        <div className="mt-8 rounded-2xl border border-dashed p-10 text-center">
          <h2 className="text-xl font-bold">No scheduled doses today</h2>
          <p className="mt-2 text-muted">Add a family member and medicine schedule to begin.</p>
        </div>
      )}
      <div className="mt-7 grid gap-4 lg:grid-cols-2">
        {doses.map((dose) => (
          <DoseCard
            key={dose.id}
            dose={dose}
            busy={action.isPending && action.variables?.dose.id === dose.id}
            onAction={(kind) => action.mutate({ dose, kind })}
          />
        ))}
      </div>
      {action.error && (
        <p role="alert" className="mt-4 text-red-700">
          {action.error.message}
        </p>
      )}
    </div>
  );
}
