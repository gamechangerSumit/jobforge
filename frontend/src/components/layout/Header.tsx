'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useState } from 'react';
import { useSession } from '@/lib/auth/session';
import { NotificationBell } from './NotificationBell';

type NavLink = { href: string; label: string };
function linksFor(role?: string): NavLink[] {
  const common: NavLink[] = [{ href: '/jobs', label: 'Find jobs' }];
  if (role === 'JOB_SEEKER') return [...common,
    { href: '/applications', label: 'Applications' }, { href: '/saved-jobs', label: 'Saved jobs' },
    { href: '/interviews', label: 'Interviews' }, { href: '/community', label: 'Community' },
    { href: '/profile', label: 'My profile' }];
  if (role === 'RECRUITER') return [...common,
    { href: '/recruiter/dashboard', label: 'Overview' }, { href: '/recruiter/jobs', label: 'My jobs' },
    { href: '/recruiter/interviews', label: 'Interviews' }, { href: '/recruiter/analytics', label: 'Analytics' },
    { href: '/recruiter/company', label: 'Company' }, { href: '/community', label: 'Community' }];
  if (role === 'ADMIN') return [...common,
    { href: '/admin/approvals', label: 'Approvals' }, { href: '/admin/users', label: 'Users' },
    { href: '/admin/jobs', label: 'Moderation' }, { href: '/admin/reports', label: 'Reports' },
    { href: '/admin/analytics', label: 'Analytics' }, { href: '/admin/audit', label: 'Audit' }];
  return common;
}

export function Header() {
  const { user, signOut, loading } = useSession();
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const links = linksFor(user?.role);
  const active = (href: string) => href === '/jobs' ? pathname === '/jobs' : pathname === href || pathname.startsWith(`${href}/`);
  const close = () => setOpen(false);

  return (
    <header className="sticky top-0 z-40 border-b border-slate-200/80 bg-white/90 backdrop-blur-xl">
      <div className="jf-container flex h-[72px] items-center justify-between gap-4">
        <Link href="/" onClick={close} className="flex shrink-0 items-center gap-2.5" aria-label="JobForge home">
          <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-violet-600 to-indigo-700 text-sm font-black text-white shadow-lg shadow-violet-200">J</span>
          <span className="text-[1.2rem] font-black tracking-[-.055em] text-slate-950">JobForge<span className="text-violet-600">.</span></span>
        </Link>

        <nav aria-label="Main navigation" className="hidden items-center gap-1 lg:flex">
          {links.slice(0, 5).map((item) => <Link key={item.href} href={item.href} className={`rounded-lg px-3 py-2 text-[13px] font-semibold transition-colors ${active(item.href) ? 'bg-violet-50 text-violet-700' : 'text-slate-600 hover:bg-slate-50 hover:text-slate-950'}`}>{item.label}</Link>)}
          {links.length > 5 && <details className="relative">
            <summary className="cursor-pointer list-none rounded-lg px-3 py-2 text-[13px] font-semibold text-slate-600 hover:bg-slate-50">More <span aria-hidden="true">⌄</span></summary>
            <div className="absolute right-0 top-11 z-50 min-w-48 rounded-xl border border-slate-200 bg-white p-2 shadow-xl">
              {links.slice(5).map((item) => <Link key={item.href} href={item.href} className="block rounded-lg px-3 py-2.5 text-sm font-medium text-slate-700 hover:bg-violet-50 hover:text-violet-700">{item.label}</Link>)}
            </div>
          </details>}
        </nav>

        <div className="hidden items-center gap-3 lg:flex">
          {loading ? <span className="h-9 w-24 animate-pulse rounded-lg bg-slate-100" /> : user ? <>
            <NotificationBell />
            <Link href="/account" className="flex items-center gap-2 rounded-full border border-slate-200 py-1 pl-1 pr-3 hover:border-violet-200 hover:bg-violet-50/60">
              <span className="flex h-8 w-8 items-center justify-center rounded-full bg-violet-100 text-xs font-bold text-violet-800">{(user.firstName?.[0] ?? 'U').toUpperCase()}</span>
              <span className="max-w-28 truncate text-xs font-bold text-slate-700">{user.firstName || 'Account'}</span>
            </Link>
            <button onClick={() => void signOut()} className="rounded-lg px-3 py-2 text-xs font-bold text-slate-500 hover:bg-slate-100 hover:text-slate-900">Sign out</button>
          </> : <>
            <Link href="/login" className="rounded-lg px-3 py-2 text-sm font-semibold text-slate-600 hover:text-slate-950">Sign in</Link>
            <Link href="/register" className="jf-button jf-button-primary !px-4 !py-2.5 !text-xs">Get started</Link>
          </>}
        </div>

        <div className="flex items-center gap-2 lg:hidden">
          {user && <NotificationBell />}
          <button type="button" aria-label={open ? 'Close navigation menu' : 'Open navigation menu'} aria-expanded={open} onClick={() => setOpen((value) => !value)} className="flex h-10 w-10 items-center justify-center rounded-xl border border-slate-200 text-slate-700 hover:bg-slate-50">
            {open ? <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none"><path d="m6 6 12 12M18 6 6 18" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg> : <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none"><path d="M4 7h16M4 12h16M4 17h16" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>}
          </button>
        </div>
      </div>

      {open && <div className="border-t border-slate-100 bg-white px-4 pb-5 pt-3 shadow-xl lg:hidden">
        <nav aria-label="Mobile navigation" className="jf-container grid gap-1">
          {links.map((item) => <Link key={item.href} href={item.href} onClick={close} className={`rounded-xl px-4 py-3 text-sm font-semibold ${active(item.href) ? 'bg-violet-50 text-violet-700' : 'text-slate-700 hover:bg-slate-50'}`}>{item.label}</Link>)}
          {user ? <><Link href="/account" onClick={close} className="rounded-xl px-4 py-3 text-sm font-semibold text-slate-700 hover:bg-slate-50">Account settings</Link><button onClick={() => { close(); void signOut(); }} className="mt-2 rounded-xl border border-slate-200 px-4 py-3 text-left text-sm font-bold text-slate-600">Sign out</button></> : <div className="mt-2 grid grid-cols-2 gap-2"><Link href="/login" onClick={close} className="jf-button jf-button-secondary">Sign in</Link><Link href="/register" onClick={close} className="jf-button jf-button-primary">Get started</Link></div>}
        </nav>
      </div>}
    </header>
  );
}
