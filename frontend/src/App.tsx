import { Navigate, Route, Routes } from 'react-router-dom';
import { lazy, Suspense } from 'react';
import { ProtectedRoute, WriteProtectedRoute } from './auth/ProtectedRoute';
import { AppShell } from './components/AppShell';
const AuthPage = lazy(() => import('./routes/AuthPage').then((m) => ({ default: m.AuthPage })));
const FamilyPage = lazy(() =>
  import('./routes/FamilyPage').then((m) => ({ default: m.FamilyPage }))
);
const InvitePage = lazy(() =>
  import('./routes/InvitePage').then((m) => ({ default: m.InvitePage }))
);
const MedicineFormPage = lazy(() =>
  import('./routes/MedicineFormPage').then((m) => ({ default: m.MedicineFormPage }))
);
const PersonPage = lazy(() =>
  import('./routes/PersonPage').then((m) => ({ default: m.PersonPage }))
);
const TodayPage = lazy(() => import('./routes/TodayPage').then((m) => ({ default: m.TodayPage })));
const StockPage = lazy(() => import('./routes/StockPage').then((m) => ({ default: m.StockPage })));
const AdherencePage = lazy(() =>
  import('./routes/AdherencePage').then((m) => ({ default: m.AdherencePage }))
);
const HouseholdPage = lazy(() =>
  import('./routes/HouseholdPage').then((m) => ({ default: m.HouseholdPage }))
);
const SettingsPage = lazy(() =>
  import('./routes/SettingsPage').then((m) => ({ default: m.SettingsPage }))
);
const PrintPage = lazy(() => import('./routes/PrintPage').then((m) => ({ default: m.PrintPage })));

function ProtectedApp() {
  return (
    <ProtectedRoute>
      <AppShell>
        <Routes>
          <Route path="/" element={<TodayPage />} />
          <Route path="/people" element={<FamilyPage />} />
          <Route path="/people/:id" element={<PersonPage />} />
          <Route
            path="/medicines/new"
            element={
              <WriteProtectedRoute>
                <MedicineFormPage />
              </WriteProtectedRoute>
            }
          />
          <Route
            path="/medicines/:id"
            element={
              <WriteProtectedRoute>
                <MedicineFormPage />
              </WriteProtectedRoute>
            }
          />
          <Route path="/stock" element={<StockPage />} />
          <Route path="/adherence" element={<AdherencePage />} />
          <Route path="/household" element={<HouseholdPage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/people/:id/print" element={<PrintPage />} />
          <Route path="/invite/:token" element={<InvitePage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AppShell>
    </ProtectedRoute>
  );
}
export function App() {
  return (
    <Suspense
      fallback={
        <p role="status" className="py-16 text-center">
          Loading page…
        </p>
      }
    >
      <Routes>
        <Route path="/login" element={<AuthPage mode="login" />} />
        <Route path="/register" element={<AuthPage mode="register" />} />
        <Route path="/*" element={<ProtectedApp />} />
      </Routes>
    </Suspense>
  );
}
