import { avatarSrc } from '@/lib/api/users';

/** Public avatar image with an initials fallback. Plain <img>: the endpoint is same-origin and cacheable. */
export function Avatar({ userId, name, size = 40, hasAvatar = true }: { userId: string; name: string; size?: number; hasAvatar?: boolean }) {
  const initials = name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]?.toUpperCase()).join('') || '?';
  if (!hasAvatar) {
    return <span aria-hidden className="inline-flex items-center justify-center rounded-full bg-slate-200 text-sm font-bold text-slate-700" style={{ width: size, height: size }}>{initials}</span>;
  }
  // eslint-disable-next-line @next/next/no-img-element
  return <img src={avatarSrc(userId)} alt={`${name} avatar`} width={size} height={size} className="rounded-full object-cover" style={{ width: size, height: size }} />;
}
