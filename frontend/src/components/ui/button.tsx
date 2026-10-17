import { Slot } from '@radix-ui/react-slot';
import { cva, type VariantProps } from 'class-variance-authority';
import type { ButtonHTMLAttributes } from 'react';
import { cn } from '../../lib/utils';
const styles = cva(
  'inline-flex min-h-12 items-center justify-center rounded-xl px-4 font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-60',
  {
    variants: {
      variant: {
        primary: 'bg-teal-700 text-white hover:bg-teal-800',
        secondary: 'border bg-surface hover:bg-slate-100 dark:hover:bg-slate-800',
        ghost: 'hover:bg-slate-100 dark:hover:bg-slate-800'
      }
    },
    defaultVariants: { variant: 'primary' }
  }
);
type Props = ButtonHTMLAttributes<HTMLButtonElement> &
  VariantProps<typeof styles> & { asChild?: boolean };
export function Button({ asChild, className, variant, ...props }: Props) {
  const Component = asChild ? Slot : 'button';
  return <Component className={cn(styles({ variant }), className)} {...props} />;
}
