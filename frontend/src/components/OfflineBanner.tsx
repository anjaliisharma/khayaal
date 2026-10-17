import { useEffect, useState } from 'react';
export function OfflineBanner() {
  const [online, setOnline] = useState(navigator.onLine);
  useEffect(() => {
    const update = () => setOnline(navigator.onLine);
    addEventListener('online', update);
    addEventListener('offline', update);
    return () => {
      removeEventListener('online', update);
      removeEventListener('offline', update);
    };
  }, []);
  return online ? null : (
    <div role="status" className="bg-amber-100 px-4 py-3 text-center font-semibold text-amber-950">
      You’re offline. The app shell remains available, but viewing or changing medicine data
      requires a connection.
    </div>
  );
}
