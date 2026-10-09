'use client';
import { useCallback, useId, useRef, useState, type ReactNode } from 'react';
import { Button } from './Button';

const MAX_REASON = 500;

/**
 * Accessible replacement for window.prompt: a native modal <dialog> (focus trap, Escape to cancel, labelled)
 * that resolves with the trimmed reason or null when cancelled.
 */
export function useReasonDialog(): { ask: (title: string, minLength?: number) => Promise<string | null>; dialog: ReactNode } {
  const ref = useRef<HTMLDialogElement>(null);
  const resolver = useRef<((value: string | null) => void) | null>(null);
  const titleId = useId();
  const [title, setTitle] = useState('');
  const [min, setMin] = useState(1);
  const [value, setValue] = useState('');
  const [error, setError] = useState<string | null>(null);

  const ask = useCallback((nextTitle: string, minLength = 1) => new Promise<string | null>((resolve) => {
    resolver.current?.(null);
    resolver.current = resolve;
    setTitle(nextTitle);
    setMin(minLength);
    setValue('');
    setError(null);
    ref.current?.showModal?.();
  }), []);

  const finish = (result: string | null) => {
    ref.current?.close?.();
    resolver.current?.(result);
    resolver.current = null;
  };

  const dialog = (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      className="w-full max-w-md rounded-xl p-0 backdrop:bg-black/40"
      onCancel={(e) => { e.preventDefault(); finish(null); }}
    >
      <form
        className="space-y-3 p-5"
        onSubmit={(e) => {
          e.preventDefault();
          const reason = value.trim();
          if (reason.length < min) { setError(`Please enter at least ${min} character${min === 1 ? '' : 's'}.`); return; }
          finish(reason.slice(0, MAX_REASON));
        }}
      >
        <h2 id={titleId} className="text-lg font-bold">{title}</h2>
        <label className="block text-sm font-semibold" htmlFor={`${titleId}-reason`}>Reason (required, max {MAX_REASON} characters)</label>
        <textarea
          id={`${titleId}-reason`}
          className="w-full rounded-lg border p-2"
          rows={4}
          maxLength={MAX_REASON}
          value={value}
          onChange={(e) => setValue(e.target.value)}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${titleId}-error` : undefined}
        />
        {error && <p id={`${titleId}-error`} role="alert" className="text-sm text-red-700">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" onClick={() => finish(null)}>Cancel</Button>
          <Button type="submit" variant="danger">Confirm</Button>
        </div>
      </form>
    </dialog>
  );

  return { ask, dialog };
}
