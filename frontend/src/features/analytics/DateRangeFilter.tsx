'use client';
import { useEffect, useId, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { matchingPreset, presetRange, RANGE_PRESETS, validateRange } from '@/lib/analytics/range';
import type { DateRangeParams } from '@/types/analytics';

/** Preset buttons plus custom from/to dates (UTC). Only a valid range is ever emitted via `onChange`. */
export function DateRangeFilter({ value, onChange }: { value: DateRangeParams; onChange: (range: DateRangeParams) => void }) {
  const id = useId();
  const [draft, setDraft] = useState(value);
  const [error, setError] = useState<string | null>(null);
  const active = matchingPreset(value);

  useEffect(() => { setDraft(value); setError(null); }, [value]);

  const apply = () => {
    const message = validateRange(draft);
    setError(message);
    if (!message) onChange(draft);
  };

  return (
    <form
      aria-label="Date range"
      className="mt-6 flex flex-wrap items-end gap-3"
      onSubmit={(event) => { event.preventDefault(); apply(); }}
    >
      <div role="group" aria-label="Quick ranges" className="flex gap-2">
        {RANGE_PRESETS.map((preset) => (
          <button
            key={preset.id}
            type="button"
            aria-pressed={active === preset.id}
            onClick={() => onChange(presetRange(preset.days))}
            className={`rounded-lg border px-3 py-2 text-sm font-semibold ${active === preset.id ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-300 bg-white text-slate-800 hover:bg-slate-50'}`}
          >
            {preset.label}
          </button>
        ))}
      </div>
      <div>
        <label htmlFor={`${id}-from`} className="block text-xs font-semibold text-slate-600">From (UTC)</label>
        <input id={`${id}-from`} type="date" value={draft.from} onChange={(e) => setDraft({ ...draft, from: e.target.value })}
          aria-invalid={error ? true : undefined} aria-describedby={error ? `${id}-error` : undefined}
          className="rounded-lg border border-slate-300 px-3 py-2 text-sm" />
      </div>
      <div>
        <label htmlFor={`${id}-to`} className="block text-xs font-semibold text-slate-600">To (UTC)</label>
        <input id={`${id}-to`} type="date" value={draft.to} onChange={(e) => setDraft({ ...draft, to: e.target.value })}
          aria-invalid={error ? true : undefined} aria-describedby={error ? `${id}-error` : undefined}
          className="rounded-lg border border-slate-300 px-3 py-2 text-sm" />
      </div>
      <Button type="submit">Apply</Button>
      {error && <p id={`${id}-error`} role="alert" className="w-full text-sm text-red-700">{error}</p>}
    </form>
  );
}
