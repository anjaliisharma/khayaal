import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { Plus } from 'lucide-react';
import { apiFetch } from '../api/client';
import type { Medicine, Recipient } from '../api/types';
import { useSelectedHousehold } from '../hooks/useHousehold';
import { Button } from '../components/ui/button';
export function PersonPage() {
  const { id } = useParams();
  const household = useSelectedHousehold();
  const queryClient = useQueryClient();
  const logAsNeeded = useMutation({
    mutationFn: (medicineId: string) =>
      apiFetch(`/api/medicines/${medicineId}/doses/prn`, {
        method: 'POST',
        headers: { 'Idempotency-Key': crypto.randomUUID() },
        body: '{}'
      }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['today'] }),
        queryClient.invalidateQueries({ queryKey: ['stock'] })
      ]);
    }
  });
  const people = useQuery({
    queryKey: ['people', household.selected?.id],
    enabled: !!household.selected,
    queryFn: () => apiFetch<Recipient[]>(`/api/households/${household.selected!.id}/people`)
  });
  const medicines = useQuery({
    queryKey: ['medicines', id],
    enabled: !!id,
    queryFn: () => apiFetch<Medicine[]>(`/api/people/${id}/medicines`)
  });
  const person = people.data?.find((p) => p.id === id);
  return (
    <div>
      <Link to="/people" className="text-sm font-semibold text-teal-700">
        ← Family
      </Link>
      <div className="mt-4 flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold">{person?.name ?? 'Person details'}</h1>
          <p className="mt-1 text-muted">{person?.timezone}</p>
        </div>
        <div className="flex gap-2">
          <Button asChild variant="secondary">
            <Link to={`/people/${id}/print`}>Print medicine list</Link>
          </Button>
          {household.selected?.role !== 'VIEWER' && (
            <Button asChild>
              <Link to={`/medicines/new?person=${id}`}>
                <Plus className="mr-2 h-5 w-5" />
                Add medicine
              </Link>
            </Button>
          )}
        </div>
      </div>
      <h2 className="mt-9 text-xl font-bold">Medicines</h2>
      {(people.isLoading || medicines.isLoading) && <p role="status">Loading medicines…</p>}
      {(people.error || medicines.error) && (
        <p role="alert" className="mt-4 text-red-700">
          {people.error?.message ?? medicines.error?.message}
        </p>
      )}
      <div className="mt-4 space-y-3">
        {medicines.data?.map((medicine) => {
          return (
            <article className="rounded-2xl border bg-surface p-5" key={medicine.id}>
              <div className="flex flex-wrap justify-between gap-3">
                <div>
                  <h3 className="font-bold">{medicine.name}</h3>
                  <p className="text-sm text-muted">
                    {medicine.strength} · {medicine.form}
                  </p>
                  {medicine.asNeeded && <p className="mt-1 text-sm font-semibold">As needed</p>}
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-sm">{medicine.active ? 'Active' : 'Archived'}</span>
                  {household.selected?.role !== 'VIEWER' &&
                    medicine.asNeeded &&
                    medicine.active && (
                      <Button
                        disabled={logAsNeeded.isPending && logAsNeeded.variables === medicine.id}
                        onClick={() => logAsNeeded.mutate(medicine.id)}
                      >
                        Log taken now
                      </Button>
                    )}
                  {household.selected?.role !== 'VIEWER' && !medicine.asNeeded && (
                    <Button asChild variant="secondary">
                      <Link to={`/medicines/${medicine.id}?person=${id}`}>Change schedule</Link>
                    </Button>
                  )}
                </div>
              </div>
            </article>
          );
        })}
      </div>
      {logAsNeeded.isSuccess && (
        <p role="status" className="mt-4">
          As-needed dose recorded.
        </p>
      )}
      {logAsNeeded.error && (
        <p role="alert" className="mt-4 text-red-700">
          {logAsNeeded.error.message}
        </p>
      )}
      {medicines.isSuccess && medicines.data.length === 0 && (
        <p className="mt-5 text-muted">
          No medicines added. Enter medicine details exactly as provided by the doctor or
          pharmacist.
        </p>
      )}
    </div>
  );
}
