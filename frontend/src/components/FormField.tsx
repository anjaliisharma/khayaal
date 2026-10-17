export function FormField({
  label,
  error,
  children
}: {
  label: string;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <label className="block space-y-2">
      <span className="font-medium">{label}</span>
      {children}
      {error && (
        <span className="block text-sm text-red-700" role="alert">
          {error}
        </span>
      )}
    </label>
  );
}
export const inputClass = 'min-h-12 w-full rounded-xl border bg-surface px-3 text-base';
