'use client';
import { useEffect, useId, useRef, useState } from 'react';
import { searchUsers, type UserSuggestion } from '@/lib/api/users';

/** Returns the @-token being typed at the caret (e.g. "@as" -> {start, query:"as"}) or null. */
export function activeMention(text: string, caret: number): { start: number; query: string } | null {
  const before = text.slice(0, caret);
  const m = /(^|\s)@([a-z0-9_]{1,30})$/i.exec(before);
  if (!m) return null;
  return { start: before.length - m[2].length - 1, query: m[2] };
}

/** Textarea with @mention autocomplete backed by GET /users/search (max 10). Combobox pattern for keyboard and screen readers. */
export function MentionInput({ value, onChange, label, rows = 4, maxLength }: { value: string; onChange: (v: string) => void; label: string; rows?: number; maxLength?: number }) {
  const listId = useId();
  const ref = useRef<HTMLTextAreaElement>(null);
  const [mention, setMention] = useState<{ start: number; query: string } | null>(null);
  const [options, setOptions] = useState<UserSuggestion[]>([]);
  const [index, setIndex] = useState(0);

  useEffect(() => {
    if (!mention) { setOptions([]); return; }
    let active = true;
    const t = setTimeout(() => {
      searchUsers(mention.query).then((r) => { if (active) { setOptions(r.slice(0, 10)); setIndex(0); } }).catch(() => { if (active) setOptions([]); });
    }, 200); // debounce
    return () => { active = false; clearTimeout(t); };
  }, [mention]);

  const track = (el: HTMLTextAreaElement) => setMention(activeMention(el.value, el.selectionStart));
  const pick = (u: UserSuggestion) => {
    if (!mention || !ref.current) return;
    const caret = ref.current.selectionStart;
    const next = `${value.slice(0, mention.start)}@${u.handle} ${value.slice(caret)}`;
    onChange(maxLength ? next.slice(0, maxLength) : next);
    setMention(null); setOptions([]);
    ref.current.focus();
  };
  const open = options.length > 0;
  return (
    <div className="relative">
      <label className="mb-1 block text-sm font-semibold">{label}
        <textarea
          ref={ref} rows={rows} maxLength={maxLength} value={value}
          role="combobox" aria-expanded={open} aria-controls={listId} aria-autocomplete="list"
          aria-activedescendant={open ? `${listId}-${index}` : undefined}
          className="mt-1 w-full rounded-lg border border-slate-300 p-3 text-sm font-normal"
          onChange={(e) => { onChange(e.target.value); track(e.target); }}
          onClick={(e) => track(e.currentTarget)}
          onKeyUp={(e) => { if (!['ArrowDown', 'ArrowUp', 'Enter', 'Escape'].includes(e.key)) track(e.currentTarget); }}
          onKeyDown={(e) => {
            if (!open) return;
            if (e.key === 'ArrowDown') { e.preventDefault(); setIndex((i) => (i + 1) % options.length); }
            else if (e.key === 'ArrowUp') { e.preventDefault(); setIndex((i) => (i - 1 + options.length) % options.length); }
            else if (e.key === 'Enter' || e.key === 'Tab') { e.preventDefault(); pick(options[index]); }
            else if (e.key === 'Escape') { setMention(null); setOptions([]); }
          }}
        />
      </label>
      {open && (
        <ul id={listId} role="listbox" className="absolute z-10 mt-1 w-full rounded-lg border bg-white shadow">
          {options.map((u, i) => (
            <li key={u.id} id={`${listId}-${i}`} role="option" aria-selected={i === index}
              className={`cursor-pointer px-3 py-2 text-sm ${i === index ? 'bg-slate-100' : ''}`}
              onMouseDown={(e) => { e.preventDefault(); pick(u); }}>
              {u.firstName} {u.lastName} <span className="text-slate-500">@{u.handle}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
