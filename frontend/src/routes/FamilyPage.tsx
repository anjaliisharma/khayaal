import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Plus, UserRound } from 'lucide-react';
import { apiFetch } from '../api/client';
import type { Household, Recipient } from '../api/types';
import { useSelectedHousehold } from '../hooks/useHousehold';
import { Button } from '../components/ui/button';
import { FormField, inputClass } from '../components/FormField';
export function FamilyPage() {
  const households = useSelectedHousehold();
  const qc = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState('');
  const [timezone, setTimezone] = useState(
    Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
  );
  const people = useQuery({
    queryKey: ['people', households.selected?.id],
    enabled: !!households.selected,
    queryFn: () => apiFetch<Recipient[]>(`/api/households/${households.selected!.id}/people`)
  });
  const createHousehold = useMutation({
    mutationFn: () =>
      apiFetch<Household>('/api/households', {
        method: 'POST',
        body: JSON.stringify({ name: 'My household', defaultTimezone: timezone })
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['households'] })
  });
  const createPerson = useMutation({
    mutationFn: () =>
      apiFetch<Recipient>(`/api/households/${households.selected!.id}/people`, {
        method: 'POST',
        body: JSON.stringify({ name, timezone })
      }),
    onSuccess: () => {
      setName('');
      setShowForm(false);
      void qc.invalidateQueries({ queryKey: ['people'] });
    }
  });
  if (households.isLoading) return <p role="status">Loading family…</p>;
  if (!households.selected)
    return (
      <div className="mx-auto max-w-lg py-12 text-center">
        <UserRound className="mx-auto h-12 w-12 text-teal-700" />
        <h1 className="mt-4 text-3xl font-bold">Create your household</h1>
        <p className="mt-2 text-muted">
          A household groups the people and routines you help manage.
        </p>
        <Button
          className="mt-6"
          onClick={() => createHousehold.mutate()}
          disabled={createHousehold.isPending}
        >
          Create my household
        </Button>
        {createHousehold.error && (
          <p role="alert" className="mt-3 text-red-700">
            {createHousehold.error.message}
          </p>
        )}
      </div>
    );
  return (
    <div>
      <div className="flex items-center justify-between gap-4">
        <div>
          <p className="text-sm font-semibold text-teal-700">{households.selected.name}</p>
          <h1 className="text-3xl font-bold">Family</h1>
        </div>
        {households.selected.role !== 'VIEWER' && (
          <Button onClick={() => setShowForm((v) => !v)}>
            <Plus className="mr-2 h-5 w-5" />
            Add person
          </Button>
        )}
      </div>
      {showForm && (
        <form
          className="mt-6 grid gap-4 rounded-2xl border bg-surface p-5 sm:grid-cols-2"
          onSubmit={(e) => {
            e.preventDefault();
            createPerson.mutate();
          }}
        >
          <FormField label="Name">
            <input
              required
              className={inputClass}
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </FormField>
          <FormField label="Timezone">
            <input
              required
              className={inputClass}
              value={timezone}
              onChange={(e) => setTimezone(e.target.value)}
            />
          </FormField>
          <Button className="sm:col-span-2" disabled={createPerson.isPending}>
            Save person
          </Button>
        </form>
      )}
      {(people.error || createPerson.error) && (
        <p role="alert" className="mt-4 text-red-700">
          {people.error?.message ?? createPerson.error?.message}
        </p>
      )}
      <div className="mt-7 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {people.data
          ?.filter((p) => !p.archived)
          .map((person) => (
            <Link
              key={person.id}
              to={`/people/${person.id}`}
              className="rounded-2xl border bg-surface p-5 hover:border-teal-500"
            >
              <h2 className="text-xl font-bold">{person.name}</h2>
              <p className="mt-2 text-sm text-muted">{person.timezone}</p>
            </Link>
          ))}
      </div>
      {people.isSuccess && people.data.length === 0 && (
        <p className="mt-10 text-center text-muted">No family members yet.</p>
      )}
    </div>
  );
}
