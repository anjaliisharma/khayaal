import { Moon, Sun } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Button } from './ui/button';
export function ThemeToggle() {
  const [dark, setDark] = useState(() => localStorage.getItem('khayaal-theme') === 'dark');
  useEffect(() => {
    document.documentElement.classList.toggle('dark', dark);
    localStorage.setItem('khayaal-theme', dark ? 'dark' : 'light');
  }, [dark]);
  return (
    <Button
      variant="ghost"
      aria-label={`Switch to ${dark ? 'light' : 'dark'} theme`}
      onClick={() => setDark((value) => !value)}
    >
      {dark ? <Sun aria-hidden="true" /> : <Moon aria-hidden="true" />}
    </Button>
  );
}
