/** Time-zone helpers for interviews (no date library: Intl only). The API stores UTC instants plus an IANA zone. */

export function isValidTimeZone(timeZone: string): boolean {
  if (!timeZone) return false;
  try {
    new Intl.DateTimeFormat('en-US', { timeZone });
    return true;
  } catch {
    return false;
  }
}

export function defaultTimeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  } catch {
    return 'UTC';
  }
}

/** Offset of `timeZone` from UTC in minutes at the given moment. */
function offsetMinutes(timeZone: string, at: number): number {
  const seconds = Math.floor(at / 1000) * 1000;
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-US', {
      timeZone, hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit',
    }).formatToParts(new Date(seconds)).map((p) => [p.type, p.value]),
  );
  const asUtc = Date.UTC(+parts.year, +parts.month - 1, +parts.day, +parts.hour, +parts.minute, +parts.second);
  return Math.round((asUtc - seconds) / 60000);
}

/** Wall-clock `YYYY-MM-DD` + `HH:mm` in `timeZone` to an ISO-8601 UTC instant (`…Z`); null when input is invalid. */
export function zonedToInstant(date: string, time: string, timeZone: string): string | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !/^\d{2}:\d{2}$/.test(time) || !isValidTimeZone(timeZone)) return null;
  const [y, m, d] = date.split('-').map(Number);
  const [hh, mm] = time.split(':').map(Number);
  const guess = Date.UTC(y, m - 1, d, hh, mm);
  if (Number.isNaN(guess)) return null;
  let ts = guess - offsetMinutes(timeZone, guess) * 60000;
  ts = guess - offsetMinutes(timeZone, ts) * 60000; // second pass settles DST boundaries
  return new Date(ts).toISOString().replace('.000Z', 'Z');
}

/** Wall-clock parts of an instant in `timeZone`. */
export function instantToZoned(iso: string, timeZone: string): { date: string; time: string } {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-US', {
      timeZone, hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
    }).formatToParts(new Date(iso)).map((p) => [p.type, p.value]),
  );
  return { date: `${parts.year}-${parts.month}-${parts.day}`, time: `${parts.hour}:${parts.minute}` };
}

/** e.g. "Mon, 12 Oct 2026, 15:30 (Asia/Kolkata)". Falls back to UTC when the stored zone is unknown to the browser. */
export function formatInZone(iso: string, timeZone: string): string {
  const zone = isValidTimeZone(timeZone) ? timeZone : 'UTC';
  const text = new Intl.DateTimeFormat('en-GB', {
    timeZone: zone, weekday: 'short', day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).format(new Date(iso));
  return `${text} (${zone})`;
}

export const hasStarted = (iso: string, now = Date.now()) => new Date(iso).getTime() <= now;

/** Only http(s) values may become links; anything else (addresses, phone numbers) renders as text. */
export function isHttpUrl(value: string): boolean {
  return /^https?:\/\/\S+$/i.test(value.trim());
}
