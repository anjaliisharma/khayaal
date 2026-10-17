import { useMutation, useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { apiFetch, setAccessToken } from '../api/client';
import { Button } from '../components/ui/button';
import { useSelectedHousehold } from '../hooks/useHousehold';
interface Preferences {
  emailEnabled: boolean;
  telegramEnabled: boolean;
  discreetMode: boolean;
  doseMissedEnabled: boolean;
  quietHoursStart: string | null;
  quietHoursEnd: string | null;
  timezone: string;
}
export function SettingsPage() {
  const household = useSelectedHousehold();
  const query = useQuery({
    queryKey: ['preferences'],
    queryFn: () => apiFetch<Preferences>('/api/notification-preferences')
  });
  const [prefs, setPrefs] = useState<Preferences | null>(null);
  const [deleteConfirmation, setDeleteConfirmation] = useState('');
  const [pharmacyUrl, setPharmacyUrl] = useState('');
  useEffect(() => {
    if (query.data) setPrefs(query.data);
  }, [query.data]);
  const save = useMutation({
    mutationFn: () =>
      apiFetch<Preferences>('/api/notification-preferences', {
        method: 'PUT',
        body: JSON.stringify(prefs)
      })
  });
  const link = useMutation({
    mutationFn: () =>
      apiFetch<{ code: string; expiresAt: string }>('/api/telegram/link-code', { method: 'POST' })
  });
  const testNotification = useMutation({
    mutationFn: (channel: 'EMAIL' | 'TELEGRAM') =>
      apiFetch<void>(`/api/notifications/test?channel=${channel}`, { method: 'POST' })
  });
  const exportData = useMutation({
    mutationFn: () => apiFetch<unknown>('/api/export'),
    onSuccess: (data) => {
      const url = URL.createObjectURL(
        new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
      );
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `khayaal-export-${new Date().toISOString().slice(0, 10)}.json`;
      anchor.click();
      URL.revokeObjectURL(url);
    }
  });
  const deleteAccount = useMutation({
    mutationFn: () =>
      apiFetch<void>(`/api/auth/account?confirmation=${encodeURIComponent(deleteConfirmation)}`, {
        method: 'DELETE'
      }),
    onSuccess: () => {
      setAccessToken(null);
      window.location.assign('/login');
    }
  });
  const pharmacy = useQuery({
    queryKey: ['pharmacy-link', household.selected?.id],
    enabled: !!household.selected,
    queryFn: () =>
      apiFetch<{ urlTemplate: string | null }>(
        `/api/households/${household.selected!.id}/pharmacy-link`
      )
  });
  useEffect(() => {
    if (pharmacy.data) setPharmacyUrl(pharmacy.data.urlTemplate ?? '');
  }, [pharmacy.data]);
  const savePharmacy = useMutation({
    mutationFn: () =>
      apiFetch(`/api/households/${household.selected!.id}/pharmacy-link`, {
        method: 'PUT',
        body: JSON.stringify({ urlTemplate: pharmacyUrl || null })
      })
  });
  const set = (key: keyof Preferences, value: unknown) =>
    setPrefs((p) => (p ? { ...p, [key]: value } : p));
  return (
    <div>
      <h1 className="text-3xl font-bold">Settings</h1>
      {!prefs ? (
        <p role="status" className="mt-6">
          Loading settings…
        </p>
      ) : (
        <form
          className="mt-7 max-w-2xl space-y-5"
          onSubmit={(e) => {
            e.preventDefault();
            save.mutate();
          }}
        >
          <fieldset className="space-y-4 rounded-2xl border p-5">
            <legend className="font-bold">Notifications</legend>
            {[
              ['emailEnabled', 'Email'],
              ['telegramEnabled', 'Telegram'],
              ['discreetMode', 'Discreet messages'],
              ['doseMissedEnabled', 'Missed-dose follow-ups']
            ].map(([key, label]) => (
              <label key={key} className="flex min-h-12 items-center justify-between gap-4">
                <span>{label}</span>
                <input
                  type="checkbox"
                  checked={Boolean(prefs[key as keyof Preferences])}
                  onChange={(e) => set(key as keyof Preferences, e.target.checked)}
                />
              </label>
            ))}
            <div className="grid grid-cols-2 gap-4">
              <label>
                Quiet from
                <input
                  className="mt-2 w-full rounded-xl border p-3"
                  type="time"
                  value={prefs.quietHoursStart ?? ''}
                  onChange={(e) => set('quietHoursStart', e.target.value || null)}
                />
              </label>
              <label>
                Quiet until
                <input
                  className="mt-2 w-full rounded-xl border p-3"
                  type="time"
                  value={prefs.quietHoursEnd ?? ''}
                  onChange={(e) => set('quietHoursEnd', e.target.value || null)}
                />
              </label>
            </div>
            <label className="block">
              Timezone
              <input
                className="mt-2 w-full rounded-xl border p-3"
                value={prefs.timezone}
                onChange={(e) => set('timezone', e.target.value)}
                placeholder="Asia/Kolkata"
                required
              />
            </label>
            <div className="flex flex-wrap gap-3">
              <Button
                type="button"
                variant="secondary"
                onClick={() => testNotification.mutate('EMAIL')}
              >
                Send test email
              </Button>
              <Button
                type="button"
                variant="secondary"
                onClick={() => testNotification.mutate('TELEGRAM')}
              >
                Send test Telegram
              </Button>
            </div>
            {testNotification.isSuccess && <p role="status">Test notification queued.</p>}
          </fieldset>
          <fieldset className="rounded-2xl border p-5">
            <legend className="font-bold">Display</legend>
            <label>
              Text size
              <select
                className="ml-3 min-h-12 rounded-xl border bg-surface px-3"
                defaultValue={localStorage.getItem('khayaal-text-size') ?? 'normal'}
                onChange={(e) => {
                  localStorage.setItem('khayaal-text-size', e.target.value);
                  document.documentElement.dataset.textSize = e.target.value;
                }}
              >
                <option value="normal">Normal</option>
                <option value="large">Large</option>
              </select>
            </label>
          </fieldset>
          {household.selected?.role === 'OWNER' && (
            <fieldset className="rounded-2xl border p-5">
              <legend className="font-bold">Pharmacy link</legend>
              <label className="block">
                Search URL template
                <input
                  className="mt-2 w-full rounded-xl border p-3"
                  type="url"
                  value={pharmacyUrl}
                  onChange={(event) => setPharmacyUrl(event.target.value)}
                  placeholder="https://pharmacy.example/search?q={name}"
                />
              </label>
              <p className="mt-2 text-sm text-muted">
                Use {'{name}'} where the medicine name belongs. Khayaal opens the link but never
                places an order.
              </p>
              <Button
                type="button"
                variant="secondary"
                className="mt-3"
                disabled={savePharmacy.isPending}
                onClick={() => savePharmacy.mutate()}
              >
                Save pharmacy link
              </Button>
              {savePharmacy.isSuccess && <p role="status">Pharmacy link saved.</p>}
              {savePharmacy.error && <p role="alert">{savePharmacy.error.message}</p>}
            </fieldset>
          )}
          <div className="flex flex-wrap gap-3">
            <Button disabled={save.isPending}>Save settings</Button>
            <Button type="button" variant="secondary" onClick={() => link.mutate()}>
              Get Telegram link code
            </Button>
            <Button type="button" variant="secondary" onClick={() => exportData.mutate()}>
              Export my data
            </Button>
          </div>
          {link.data && (
            <p role="status">
              Telegram code: <strong>{link.data.code}</strong>
            </p>
          )}
          <fieldset className="rounded-2xl border border-red-300 p-5">
            <legend className="font-bold text-red-800">Delete account</legend>
            <p className="mb-3 text-sm text-muted">
              This permanently removes your sole-member households and anonymizes audit history. If
              your household has other members, remove them first.
            </p>
            <label>
              Type DELETE to confirm
              <input
                className="mt-2 w-full rounded-xl border p-3"
                value={deleteConfirmation}
                onChange={(event) => setDeleteConfirmation(event.target.value)}
              />
            </label>
            <Button
              type="button"
              className="mt-3 bg-red-700 text-white"
              disabled={deleteConfirmation !== 'DELETE' || deleteAccount.isPending}
              onClick={() => deleteAccount.mutate()}
            >
              Permanently delete my account
            </Button>
            {deleteAccount.error && (
              <p role="alert" className="mt-3 text-red-700">
                {deleteAccount.error.message}
              </p>
            )}
          </fieldset>
        </form>
      )}
    </div>
  );
}
