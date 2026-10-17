import { useMutation } from '@tanstack/react-query';
import { useParams, Link } from 'react-router-dom';
import { apiFetch } from '../api/client';
import type { Household } from '../api/types';
import { Button } from '../components/ui/button';
export function InvitePage() {
  const { token } = useParams();
  const accept = useMutation({
    mutationFn: () => apiFetch<Household>(`/api/invites/${token}/accept`, { method: 'POST' })
  });
  return (
    <div className="mx-auto max-w-lg py-16 text-center">
      <h1 className="text-3xl font-bold">Household invitation</h1>
      <p className="mt-3 text-muted">Join this household to help track its shared routine.</p>
      {accept.isSuccess ? (
        <>
          <p className="mt-8 text-lg">
            You joined <strong>{accept.data.name}</strong>.
          </p>
          <Button asChild className="mt-5">
            <Link to="/">Open Today</Link>
          </Button>
        </>
      ) : (
        <Button className="mt-8" onClick={() => accept.mutate()} disabled={accept.isPending}>
          {accept.isPending ? 'Joining…' : 'Accept invitation'}
        </Button>
      )}
      {accept.error && (
        <p role="alert" className="mt-4 text-red-700">
          {accept.error.message}
        </p>
      )}
    </div>
  );
}
