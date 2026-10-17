import { useQuery } from '@tanstack/react-query';
import { useParams } from 'react-router-dom';
import { apiFetch } from '../api/client';
import type { Medicine, Recipient } from '../api/types';
import { Button } from '../components/ui/button';
import { useSelectedHousehold } from '../hooks/useHousehold';
export function PrintPage() {
  const { id } = useParams();
  const household = useSelectedHousehold();
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
    <article className="print-page">
      <div className="print:hidden">
        <Button onClick={() => print()}>Print this list</Button>
      </div>
      <h1 className="mt-6 text-3xl font-bold">{person?.name} — medicine list</h1>
      <p className="mt-2">Timezone: {person?.timezone}</p>
      <table className="mt-8 w-full border-collapse">
        <thead>
          <tr>
            <th>Medicine</th>
            <th>Strength</th>
            <th>Form</th>
            <th>Instructions</th>
          </tr>
        </thead>
        <tbody>
          {medicines.data?.map((m) => (
            <tr key={m.id}>
              <td>{m.name}</td>
              <td>{m.strength}</td>
              <td>{m.form}</td>
              <td>{m.instructions}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <footer className="mt-10 border-t pt-4 text-sm">
        All information is user-entered. Khayaal is a reminder and tracking tool, not medical
        advice.
      </footer>
    </article>
  );
}
