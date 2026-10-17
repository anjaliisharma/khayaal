import { useQuery } from '@tanstack/react-query';
import { useEffect } from 'react';
import { apiFetch } from '../api/client';
import type { Household } from '../api/types';
export function useHouseholds() {
  return useQuery({
    queryKey: ['households'],
    queryFn: () => apiFetch<Household[]>('/api/households')
  });
}
export function useSelectedHousehold() {
  const query = useHouseholds();
  const saved = localStorage.getItem('khayaal-household');
  const selected = query.data?.find((h) => h.id === saved) ?? query.data?.[0];
  useEffect(() => {
    if (selected && selected.id !== saved) localStorage.setItem('khayaal-household', selected.id);
  }, [saved, selected]);
  return { ...query, selected };
}
