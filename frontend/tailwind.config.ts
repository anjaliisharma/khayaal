import type { Config } from 'tailwindcss';

export default {
  darkMode: ['class'],
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        canvas: 'hsl(var(--canvas))',
        surface: 'hsl(var(--surface))',
        ink: 'hsl(var(--ink))',
        muted: 'hsl(var(--muted))',
        brand: 'hsl(var(--brand))'
      }
    }
  },
  plugins: []
} satisfies Config;
