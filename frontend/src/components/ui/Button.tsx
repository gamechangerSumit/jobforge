import type { ButtonHTMLAttributes } from 'react';
import { clsx } from 'clsx';

type Variant = 'default' | 'danger';

export function Button({ className, variant = 'default', ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
  return (
    <button
      className={clsx(
        'inline-flex min-h-10 items-center justify-center rounded-lg px-4 py-2 text-sm font-semibold text-white transition disabled:cursor-not-allowed disabled:opacity-50',
        variant === 'danger' ? 'bg-red-700 hover:bg-red-800' : 'bg-slate-900 hover:bg-slate-700',
        className,
      )}
      {...props}
    />
  );
}
