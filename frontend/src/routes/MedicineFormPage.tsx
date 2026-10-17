import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { apiFetch } from '../api/client';
import type { Medicine, PreviewOccurrence } from '../api/types';
import { FormField, inputClass } from '../components/FormField';
import { ScheduleBuilder, type ScheduleValue } from '../components/ScheduleBuilder';
import { Button } from '../components/ui/button';
import { useSelectedHousehold } from '../hooks/useHousehold';
const initialSchedule: ScheduleValue = {
  type: 'DAILY',
  localTimes: ['08:00'],
  daysOfWeek: [],
  intervalDays: null,
  anchorDate: null
};
export function MedicineFormPage() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const person = params.get('person');
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const household = useSelectedHousehold();
  const today = new Date().toISOString().slice(0, 10);
  const [name, setName] = useState('');
  const [strength, setStrength] = useState('');
  const [form, setForm] = useState('tablet');
  const [units, setUnits] = useState('1.00');
  const [instructions, setInstructions] = useState('');
  const [startDate, setStartDate] = useState(today);
  const [endDate, setEndDate] = useState('');
  const [lead, setLead] = useState(3);
  const [asNeeded, setAsNeeded] = useState(false);
  const [schedule, setSchedule] = useState(initialSchedule);
  const [effectiveFrom, setEffectiveFrom] = useState(new Date().toISOString());
  const [includeFirstPurchase, setIncludeFirstPurchase] = useState(false);
  const [purchasePacks, setPurchasePacks] = useState(1);
  const [purchaseUnitsPerPack, setPurchaseUnitsPerPack] = useState(30);
  const payload = useMemo(
    () => ({
      name,
      strength: strength || null,
      form,
      unitsPerDose: Number(units),
      instructions: instructions || null,
      startDate,
      endDate: endDate || null,
      asNeeded,
      leadTimeDays: lead,
      schedule: {
        ...schedule,
        localTimes: schedule.localTimes.map((t) => (t.length === 5 ? `${t}:00` : t)),
        daysOfWeek: schedule.daysOfWeek,
        anchorDate: schedule.anchorDate || null
      }
    }),
    [name, strength, form, units, instructions, startDate, endDate, asNeeded, lead, schedule]
  );
  const preview = useQuery({
    queryKey: ['schedule-preview', schedule, startDate, endDate],
    enabled: !asNeeded && schedule.localTimes.every(Boolean) && !!startDate,
    queryFn: () =>
      apiFetch<PreviewOccurrence[]>('/api/medicines/schedule-preview', {
        method: 'POST',
        body: JSON.stringify({
          schedule: payload.schedule,
          timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
          startDate,
          endDate: endDate || null,
          from: new Date().toISOString(),
          days: 7
        })
      })
  });
  const save = useMutation({
    mutationFn: () =>
      id
        ? apiFetch<Medicine>(`/api/medicines/${id}/schedule`, {
            method: 'PUT',
            body: JSON.stringify({ effectiveFrom, schedule: payload.schedule })
          })
        : includeFirstPurchase
          ? apiFetch(`/api/households/${household.selected!.id}/medicines-with-purchase`, {
              method: 'POST',
              body: JSON.stringify({
                recipientId: person,
                medicine: payload,
                purchase: {
                  packCount: purchasePacks,
                  unitsPerPack: purchaseUnitsPerPack,
                  price: 0,
                  pharmacy: null,
                  purchasedOn: today,
                  batchNumber: null,
                  expiryDate: null,
                  note: 'Initial stock'
                }
              })
            })
          : apiFetch<Medicine>(`/api/people/${person}/medicines`, {
              method: 'POST',
              body: JSON.stringify(payload)
            }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['today'] }),
        queryClient.invalidateQueries({ queryKey: ['medicines', person] }),
        queryClient.invalidateQueries({ queryKey: ['stock'] })
      ]);
      navigate(`/people/${person ?? ''}`, { replace: true });
    }
  });
  if (!id && !person) return <p role="alert">Choose a family member before adding a medicine.</p>;
  return (
    <div className="mx-auto max-w-3xl">
      <Link to={person ? `/people/${person}` : '/people'} className="font-semibold text-teal-700">
        ← Back
      </Link>
      <h1 className="mt-4 text-3xl font-bold">{id ? 'Change schedule' : 'Add medicine'}</h1>
      <p className="mt-2 text-muted">
        Enter details exactly as provided by the doctor, pharmacist, or medicine label. Khayaal does
        not validate or recommend doses.
      </p>
      <form
        className="mt-7 space-y-6"
        onSubmit={(e) => {
          e.preventDefault();
          save.mutate();
        }}
      >
        {!id && (
          <>
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField label="Medicine name">
                <input
                  required
                  className={inputClass}
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                />
              </FormField>
              <FormField label="Strength (as written)">
                <input
                  className={inputClass}
                  value={strength}
                  onChange={(e) => setStrength(e.target.value)}
                />
              </FormField>
              <FormField label="Form">
                <select
                  className={inputClass}
                  value={form}
                  onChange={(e) => setForm(e.target.value)}
                >
                  <option>tablet</option>
                  <option>capsule</option>
                  <option>syrup</option>
                  <option>drops</option>
                  <option>injection</option>
                  <option>other</option>
                </select>
              </FormField>
              <FormField label="Units per scheduled dose">
                <input
                  required
                  className={inputClass}
                  type="number"
                  min="0.25"
                  step="0.25"
                  value={units}
                  onChange={(e) => setUnits(e.target.value)}
                />
              </FormField>
              <FormField label="Start date">
                <input
                  required
                  className={inputClass}
                  type="date"
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                />
              </FormField>
              <FormField label="End date (optional)">
                <input
                  className={inputClass}
                  type="date"
                  value={endDate}
                  onChange={(e) => setEndDate(e.target.value)}
                />
              </FormField>
              <FormField label="Instructions (user-entered)">
                <textarea
                  className={`${inputClass} min-h-24 py-3`}
                  value={instructions}
                  onChange={(e) => setInstructions(e.target.value)}
                />
              </FormField>
              <FormField label="Reorder lead time (days)">
                <input
                  className={inputClass}
                  type="number"
                  min="0"
                  value={lead}
                  onChange={(e) => setLead(Number(e.target.value))}
                />
              </FormField>
              <label className="flex min-h-12 items-center gap-3 sm:col-span-2">
                <input
                  type="checkbox"
                  checked={asNeeded}
                  onChange={(event) => setAsNeeded(event.target.checked)}
                />
                This is an as-needed medicine with no schedule
              </label>
            </div>
          </>
        )}
        {!id && (
          <fieldset className="rounded-2xl border p-5">
            <legend className="font-bold">Starting stock</legend>
            <label className="flex min-h-12 items-center gap-3">
              <input
                type="checkbox"
                checked={includeFirstPurchase}
                onChange={(event) => setIncludeFirstPurchase(event.target.checked)}
              />
              Add the first purchase now
            </label>
            {includeFirstPurchase && (
              <div className="mt-4 grid gap-4 sm:grid-cols-2">
                <FormField label="Initial packs">
                  <input
                    className={inputClass}
                    type="number"
                    min="1"
                    step="1"
                    value={purchasePacks}
                    onChange={(event) => setPurchasePacks(Math.trunc(Number(event.target.value)))}
                  />
                </FormField>
                <FormField label="Initial units per pack">
                  <input
                    className={inputClass}
                    type="number"
                    min="1"
                    step="1"
                    value={purchaseUnitsPerPack}
                    onChange={(event) =>
                      setPurchaseUnitsPerPack(Math.trunc(Number(event.target.value)))
                    }
                  />
                </FormField>
                <p className="sm:col-span-2" aria-live="polite">
                  This adds <strong>{purchasePacks * purchaseUnitsPerPack} whole units</strong> to
                  starting stock.
                </p>
              </div>
            )}
          </fieldset>
        )}
        {id && (
          <FormField label="Apply this schedule from">
            <input
              className={inputClass}
              type="datetime-local"
              value={effectiveFrom.slice(0, 16)}
              onChange={(e) => setEffectiveFrom(new Date(e.target.value).toISOString())}
            />
          </FormField>
        )}
        {!asNeeded && <ScheduleBuilder value={schedule} onChange={setSchedule} />}
        {!asNeeded && (
          <section aria-live="polite" className="rounded-2xl bg-slate-100 p-5 dark:bg-slate-900">
            <h2 className="font-bold">Next 7 days preview</h2>
            {preview.isLoading ? (
              <p>Updating preview…</p>
            ) : preview.error ? (
              <p className="text-red-700">Preview unavailable: {preview.error.message}</p>
            ) : (
              <ul className="mt-2 grid gap-1 sm:grid-cols-2">
                {preview.data?.slice(0, 12).map((item) => (
                  <li key={item.scheduledAt}>{new Date(item.scheduledAt).toLocaleString()}</li>
                ))}
              </ul>
            )}
          </section>
        )}
        {save.error && (
          <p role="alert" className="text-red-700">
            {save.error.message}
          </p>
        )}
        <Button
          className="w-full"
          disabled={
            save.isPending ||
            (includeFirstPurchase &&
              (purchasePacks < 1 ||
                purchaseUnitsPerPack < 1 ||
                !Number.isInteger(purchasePacks) ||
                !Number.isInteger(purchaseUnitsPerPack)))
          }
        >
          {save.isPending ? 'Saving…' : id ? 'Save new schedule' : 'Add medicine'}
        </Button>
      </form>
    </div>
  );
}
