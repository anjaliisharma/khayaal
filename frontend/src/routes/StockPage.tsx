import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, PackagePlus, ShoppingCart } from 'lucide-react';
import { useState } from 'react';
import { apiFetch } from '../api/client';
import { Button } from '../components/ui/button';
import { FormField, inputClass } from '../components/FormField';
import { useSelectedHousehold } from '../hooks/useHousehold';

interface StockRow {
  medicineId: string;
  medicineName: string;
  recipientName: string;
  balance: number;
  forecast: Forecast | null;
  reorderStatus: string | null;
  expectedBy: string | null;
}
interface Forecast {
  daysLeft: number | null;
  runOutDate: string | null;
  reorderBy: string | null;
  likelyRunOutDate?: string | null;
}
interface Lot {
  id: string;
  purchaseId: string | null;
  remainingUnits: number;
  expiryDate: string | null;
  batchNumber: string | null;
}
interface StockDetail {
  medicineId: string;
  balance: number;
  lots: Lot[];
  forecast: Forecast | null;
}
interface ReorderItem {
  medicineId: string;
  name: string;
  strength: string | null;
  balance: number;
  unitsNeeded: number;
}

export function StockPage() {
  const household = useSelectedHousehold();
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState<StockRow | null>(null);
  const [packs, setPacks] = useState(1);
  const [units, setUnits] = useState(30);
  const [price, setPrice] = useState('');
  const [purchasePharmacy, setPurchasePharmacy] = useState('');
  const [purchasedOn, setPurchasedOn] = useState(new Date().toISOString().slice(0, 10));
  const [batchNumber, setBatchNumber] = useState('');
  const [expiryDate, setExpiryDate] = useState('');
  const [purchaseNote, setPurchaseNote] = useState('');
  const [listFeedback, setListFeedback] = useState('');
  const overview = useQuery({
    queryKey: ['stock'],
    queryFn: () => apiFetch<StockRow[]>('/api/stock/overview')
  });
  const details = useQuery({
    queryKey: ['stock-detail', selected?.medicineId],
    enabled: !!selected,
    queryFn: () => apiFetch<StockDetail>(`/api/medicines/${selected!.medicineId}/stock`)
  });
  const reorderList = useQuery({
    queryKey: ['reorder-list', household.selected?.id],
    enabled: !!household.selected,
    queryFn: () => apiFetch<ReorderItem[]>(`/api/households/${household.selected!.id}/reorder-list`)
  });
  const pharmacy = useQuery({
    queryKey: ['pharmacy-link', household.selected?.id],
    enabled: !!household.selected,
    queryFn: () =>
      apiFetch<{ urlTemplate: string | null }>(
        `/api/households/${household.selected!.id}/pharmacy-link`
      )
  });
  const purchase = useMutation({
    mutationFn: () =>
      apiFetch(`/api/medicines/${selected!.medicineId}/purchases`, {
        method: 'POST',
        body: JSON.stringify({
          packCount: packs,
          unitsPerPack: units,
          price: price ? Number(price) : null,
          pharmacy: purchasePharmacy || null,
          purchasedOn,
          batchNumber: batchNumber || null,
          expiryDate: expiryDate || null,
          note: purchaseNote || null
        })
      }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['stock'] }),
        queryClient.invalidateQueries({ queryKey: ['stock-detail'] })
      ]);
      setSelected(null);
    }
  });
  const ordered = useMutation({
    mutationFn: (medicineId: string) =>
      apiFetch(`/api/medicines/${medicineId}/reorder-requests`, {
        method: 'POST',
        body: JSON.stringify({
          expectedBy: new Date(Date.now() + 3 * 86400000).toISOString().slice(0, 10)
        })
      }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['stock'] }),
        queryClient.invalidateQueries({ queryKey: ['reorder-list'] })
      ]);
    }
  });
  const voidPurchase = useMutation({
    mutationFn: (purchaseId: string) =>
      apiFetch<void>(`/api/purchases/${purchaseId}`, { method: 'DELETE' }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['stock'] }),
        queryClient.invalidateQueries({ queryKey: ['stock-detail'] })
      ]);
    }
  });
  const reorderText = (reorderList.data ?? [])
    .map(
      (item) =>
        `${item.name}${item.strength ? ` ${item.strength}` : ''}: ${item.unitsNeeded} units needed`
    )
    .join('\n');
  const copyReorderList = async () => {
    await navigator.clipboard.writeText(reorderText);
    setListFeedback('Reorder list copied.');
  };
  const shareReorderList = async () => {
    if (navigator.share)
      await navigator.share({ title: 'Khayaal reorder list', text: reorderText });
    else await copyReorderList();
  };
  if (overview.isLoading) return <p role="status">Loading stock…</p>;
  if (overview.error)
    return (
      <p role="alert" className="text-red-700">
        {overview.error.message}
      </p>
    );
  const rows = [...(overview.data ?? [])].sort(
    (a, b) =>
      (a.forecast?.daysLeft ?? Number.MAX_SAFE_INTEGER) -
      (b.forecast?.daysLeft ?? Number.MAX_SAFE_INTEGER)
  );
  return (
    <div>
      <h1 className="text-3xl font-bold">Stock and refills</h1>
      <p className="mt-2 text-muted">Balances come from purchases and recorded dose actions.</p>
      {rows.some((r) => r.balance <= 0) && (
        <div
          role="alert"
          className="mt-6 flex gap-3 rounded-2xl border border-red-400 bg-red-50 p-4 text-red-900"
        >
          <AlertTriangle />
          Some medicines are out of stock. Review them below.
        </div>
      )}
      <div className="mt-7 space-y-4">
        {rows.map((row) => (
          <article key={row.medicineId} className="rounded-2xl border bg-surface p-5">
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div>
                <h2 className="text-lg font-bold">{row.medicineName}</h2>
                <p className="text-muted">
                  {row.recipientName} · {row.balance} units remaining
                </p>
                <p className="mt-1 font-medium">
                  {row.forecast
                    ? `${row.forecast.daysLeft} days left · runs out ${row.forecast.runOutDate ?? 'after current schedule'}${row.forecast.reorderBy ? ` · reorder by ${row.forecast.reorderBy}` : ''}`
                    : 'Forecast unavailable for as-needed or unscheduled medicine'}
                </p>
                {row.reorderStatus === 'ORDERED' && (
                  <p role="status" className="mt-2 font-semibold text-teal-700">
                    Ordered{row.expectedBy ? ` · expected by ${row.expectedBy}` : ''}
                  </p>
                )}
              </div>
              <div className="flex gap-2">
                {household.selected?.role !== 'VIEWER' && (
                  <Button onClick={() => setSelected(row)}>
                    <PackagePlus className="mr-2 h-5 w-5" />
                    Add purchase
                  </Button>
                )}
                {household.selected?.role !== 'VIEWER' && (
                  <Button
                    variant="secondary"
                    disabled={
                      row.reorderStatus === 'ORDERED' ||
                      (ordered.isPending && ordered.variables === row.medicineId)
                    }
                    onClick={() => ordered.mutate(row.medicineId)}
                  >
                    <ShoppingCart className="mr-2 h-5 w-5" />
                    {row.reorderStatus === 'ORDERED'
                      ? 'Ordered'
                      : ordered.isPending && ordered.variables === row.medicineId
                        ? 'Saving…'
                        : 'Mark ordered'}
                  </Button>
                )}
              </div>
            </div>
          </article>
        ))}
      </div>
      {ordered.error && (
        <p role="alert" className="mt-4 text-red-700">
          Could not mark the medicine as ordered: {ordered.error.message}
        </p>
      )}
      <section className="mt-10 rounded-2xl border bg-surface p-5" aria-labelledby="reorder-title">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 id="reorder-title" className="text-xl font-bold">
              Reorder list
            </h2>
            <p className="text-sm text-muted">Suggested quantities cover the next 30 days.</p>
          </div>
          {!!reorderList.data?.length && (
            <div className="flex gap-2">
              <Button variant="secondary" onClick={copyReorderList}>
                Copy list
              </Button>
              <Button variant="secondary" onClick={shareReorderList}>
                Share list
              </Button>
            </div>
          )}
        </div>
        {reorderList.isLoading && <p role="status">Loading reorder list…</p>}
        {reorderList.error && <p role="alert">{reorderList.error.message}</p>}
        {!reorderList.isLoading && !reorderList.data?.length && (
          <p className="mt-4 text-muted">Nothing currently needs reordering.</p>
        )}
        <ul className="mt-4 space-y-3">
          {reorderList.data?.map((item) => {
            const buyUrl = pharmacy.data?.urlTemplate?.replace(
              '{name}',
              encodeURIComponent(item.name)
            );
            return (
              <li
                key={item.medicineId}
                className="flex flex-wrap justify-between gap-3 border-t pt-3"
              >
                <span>
                  <strong>{item.name}</strong>
                  {item.strength ? ` ${item.strength}` : ''} · {item.unitsNeeded} units suggested
                </span>
                {buyUrl && (
                  <a
                    className="font-semibold text-teal-700"
                    href={buyUrl}
                    target="_blank"
                    rel="noreferrer"
                  >
                    Buy
                  </a>
                )}
              </li>
            );
          })}
        </ul>
        {listFeedback && (
          <p role="status" className="mt-3">
            {listFeedback}
          </p>
        )}
      </section>
      {!rows.length && (
        <p className="mt-10 text-center text-muted">No active medicines to track yet.</p>
      )}
      {selected && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="purchase-title"
          className="fixed inset-0 z-50 grid place-items-center bg-black/50 p-4"
        >
          <form
            className="w-full max-w-lg space-y-4 rounded-2xl bg-surface p-6"
            onSubmit={(e) => {
              e.preventDefault();
              purchase.mutate();
            }}
          >
            <h2 id="purchase-title" className="text-2xl font-bold">
              Add purchase
            </h2>
            <p>
              {selected.medicineName}: current {details.data?.balance ?? selected.balance}; adds{' '}
              {packs * units}; new balance{' '}
              {(details.data?.balance ?? selected.balance) + packs * units}.
            </p>
            <div className="grid grid-cols-2 gap-4">
              <FormField label="Packs">
                <input
                  className={inputClass}
                  min="1"
                  type="number"
                  value={packs}
                  onChange={(e) => setPacks(Number(e.target.value))}
                />
              </FormField>
              <FormField label="Purchase date">
                <input
                  className={inputClass}
                  type="date"
                  required
                  value={purchasedOn}
                  onChange={(event) => setPurchasedOn(event.target.value)}
                />
              </FormField>
              <FormField label="Price (optional)">
                <input
                  className={inputClass}
                  type="number"
                  min="0"
                  step="0.01"
                  value={price}
                  onChange={(event) => setPrice(event.target.value)}
                />
              </FormField>
              <FormField label="Pharmacy (optional)">
                <input
                  className={inputClass}
                  value={purchasePharmacy}
                  onChange={(event) => setPurchasePharmacy(event.target.value)}
                />
              </FormField>
              <FormField label="Batch number (optional)">
                <input
                  className={inputClass}
                  value={batchNumber}
                  onChange={(event) => setBatchNumber(event.target.value)}
                />
              </FormField>
              <FormField label="Expiry date (optional)">
                <input
                  className={inputClass}
                  type="date"
                  value={expiryDate}
                  onChange={(event) => setExpiryDate(event.target.value)}
                />
              </FormField>
              <FormField label="Note (optional)">
                <input
                  className={inputClass}
                  value={purchaseNote}
                  onChange={(event) => setPurchaseNote(event.target.value)}
                />
              </FormField>
              <FormField label="Units per pack">
                <input
                  className={inputClass}
                  min="1"
                  step="1"
                  type="number"
                  value={units}
                  onChange={(e) => setUnits(Math.trunc(Number(e.target.value)))}
                />
                <span className="mt-1 block text-sm text-muted">
                  Enter a whole number, such as 10 or 30.
                </span>
              </FormField>
            </div>
            {details.data?.lots.length ? (
              <details>
                <summary>Current lots</summary>
                <ul>
                  {details.data.lots.map((l) => (
                    <li
                      key={l.id}
                      className="flex flex-wrap items-center justify-between gap-2 py-1"
                    >
                      <span>
                        {l.remainingUnits} units{l.batchNumber ? ` · batch ${l.batchNumber}` : ''}
                        {l.expiryDate ? ` · expires ${l.expiryDate}` : ''}
                      </span>
                      {l.purchaseId && household.selected?.role !== 'VIEWER' && (
                        <Button
                          type="button"
                          variant="secondary"
                          disabled={voidPurchase.isPending}
                          onClick={() => voidPurchase.mutate(l.purchaseId!)}
                        >
                          Undo purchase
                        </Button>
                      )}
                    </li>
                  ))}
                </ul>
              </details>
            ) : null}
            <div className="flex gap-3">
              <Button
                disabled={purchase.isPending || packs < 1 || units < 1 || !Number.isInteger(units)}
              >
                Save purchase
              </Button>
              <Button type="button" variant="secondary" onClick={() => setSelected(null)}>
                Cancel
              </Button>
            </div>
            {purchase.error && (
              <p role="alert" className="text-red-700">
                Could not save the purchase: {purchase.error.message}
              </p>
            )}
            {voidPurchase.error && (
              <p role="alert" className="text-red-700">
                Could not undo the purchase: {voidPurchase.error.message}
              </p>
            )}
          </form>
        </div>
      )}
    </div>
  );
}
