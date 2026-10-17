import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { apiFetch } from '../api/client';
import { Button } from '../components/ui/button';
import { useSelectedHousehold } from '../hooks/useHousehold';

interface Invite {
  token: string;
  expiresAt: string;
  role: string;
}
interface Member {
  userId: string;
  displayName: string;
  email: string;
  role: 'OWNER' | 'CAREGIVER' | 'VIEWER';
}
interface PendingInvite {
  id: string;
  role: string;
  expiresAt: string;
  createdAt: string;
}

export function HouseholdPage() {
  const household = useSelectedHousehold();
  const queryClient = useQueryClient();
  const [role, setRole] = useState('CAREGIVER');
  const members = useQuery({
    queryKey: ['members', household.selected?.id],
    enabled: !!household.selected,
    queryFn: () => apiFetch<Member[]>(`/api/households/${household.selected!.id}/members`)
  });
  const pending = useQuery({
    queryKey: ['invites', household.selected?.id],
    enabled: household.selected?.role === 'OWNER',
    queryFn: () => apiFetch<PendingInvite[]>(`/api/households/${household.selected!.id}/invites`)
  });
  const invite = useMutation({
    mutationFn: () =>
      apiFetch<Invite>(`/api/households/${household.selected!.id}/invites`, {
        method: 'POST',
        body: JSON.stringify({ role, validHours: 48 })
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['invites'] })
  });
  const changeRole = useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: string }) =>
      apiFetch(`/api/households/${household.selected!.id}/members/${userId}`, {
        method: 'PUT',
        body: JSON.stringify({ role })
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['members'] })
  });
  const remove = useMutation({
    mutationFn: (userId: string) =>
      apiFetch(`/api/households/${household.selected!.id}/members/${userId}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['members'] })
  });
  const link = invite.data ? `${location.origin}/invite/${invite.data.token}` : '';
  return (
    <div>
      <h1 className="text-3xl font-bold">Household</h1>
      <p className="mt-2 text-muted">
        Manage access to {household.selected?.name ?? 'your household'}.
      </p>
      {(members.error || pending.error || invite.error || changeRole.error || remove.error) && (
        <p role="alert" className="mt-4 text-red-700">
          {members.error?.message ??
            pending.error?.message ??
            invite.error?.message ??
            changeRole.error?.message ??
            remove.error?.message}
        </p>
      )}
      <section className="mt-7">
        <h2 className="text-xl font-bold">Members</h2>
        <div className="mt-3 space-y-3">
          {members.data?.map((member) => (
            <article
              key={member.userId}
              className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border bg-surface p-4"
            >
              <div>
                <strong>{member.displayName}</strong>
                <p className="text-sm text-muted">
                  {member.email} · {member.role}
                </p>
              </div>
              {household.selected?.role === 'OWNER' && member.role !== 'OWNER' && (
                <div className="flex gap-2">
                  <select
                    aria-label={`Role for ${member.displayName}`}
                    className="min-h-12 rounded-xl border bg-surface px-3"
                    value={member.role}
                    onChange={(e) =>
                      changeRole.mutate({ userId: member.userId, role: e.target.value })
                    }
                  >
                    <option>CAREGIVER</option>
                    <option>VIEWER</option>
                  </select>
                  <Button variant="secondary" onClick={() => remove.mutate(member.userId)}>
                    Remove
                  </Button>
                </div>
              )}
            </article>
          ))}
        </div>
      </section>
      {household.selected?.role === 'OWNER' && (
        <section className="mt-7 max-w-xl rounded-2xl border bg-surface p-5">
          <h2 className="text-xl font-bold">Invite someone</h2>
          <label className="mt-4 block font-medium">
            Role
            <select
              className="ml-3 min-h-12 rounded-xl border bg-surface px-3"
              value={role}
              onChange={(e) => setRole(e.target.value)}
            >
              <option value="CAREGIVER">Caregiver</option>
              <option value="VIEWER">Viewer</option>
            </select>
          </label>
          <Button className="mt-4" onClick={() => invite.mutate()}>
            Create invitation
          </Button>
          {link && (
            <div className="mt-4 rounded-xl bg-slate-100 p-3 break-all">
              <p>{link}</p>
              <Button
                className="mt-3"
                variant="secondary"
                onClick={() => navigator.clipboard.writeText(link)}
              >
                Copy link
              </Button>
            </div>
          )}
          <h3 className="mt-6 font-bold">Pending invitations</h3>
          <ul className="mt-2">
            {pending.data?.map((item) => (
              <li key={item.id}>
                {item.role} · expires {new Date(item.expiresAt).toLocaleString()}
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
