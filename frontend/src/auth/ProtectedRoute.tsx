import { Navigate, useLocation } from 'react-router-dom';
import type { PropsWithChildren } from 'react';
import { useAuth } from './authContext';
import { useSelectedHousehold } from '../hooks/useHousehold';
export function ProtectedRoute({ children }: PropsWithChildren) {
  const auth = useAuth();
  const location = useLocation();
  if (auth.loading)
    return (
      <p role="status" className="py-16 text-center text-muted">
        Loading Khayaal…
      </p>
    );
  if (!auth.authenticated)
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  return children;
}

export function WriteProtectedRoute({ children }: PropsWithChildren) {
  const households = useSelectedHousehold();
  if (households.isLoading)
    return (
      <p role="status" className="py-16 text-center text-muted">
        Checking household access…
      </p>
    );
  if (!households.selected || households.selected.role === 'VIEWER') {
    return <Navigate to="/people" replace />;
  }
  return children;
}
