import Link from 'next/link';
import { Suspense } from 'react';
import { JobSearch } from '@/features/jobs/JobSearch';

const benefits = [
  { number: '01', title: 'Discover opportunities', body: 'Explore roles by skills, location, experience and work style. Find the right next step, not just another listing.' },
  { number: '02', title: 'Make smarter moves', body: 'Use your profile, saved roles and application activity to keep your search focused and intentional.' },
  { number: '03', title: 'Grow your network', body: 'Connect with people, join conversations and keep interviews and updates in one place.' },
];

function Arrow() {
  return <svg aria-hidden="true" viewBox="0 0 20 20" fill="none" className="h-4 w-4"><path d="M4 10h12m-5-5 5 5-5 5" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"/></svg>;
}
function Sparkle() {
  return <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" className="h-4 w-4"><path d="m12 2 1.8 6.2L20 10l-6.2 1.8L12 18l-1.8-6.2L4 10l6.2-1.8L12 2Z" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round"/><path d="m19 15 .9 2.1L22 18l-2.1.9L19 21l-.9-2.1L16 18l2.1-.9L19 15Z" fill="currentColor"/></svg>;
}

export default function HomePage() {
  return (
    <div className="overflow-hidden">
      <section className="relative isolate border-b border-violet-100 bg-white">
        <div className="jf-grid pointer-events-none absolute inset-0 -z-10 opacity-80" />
        <div className="pointer-events-none absolute -right-40 -top-52 -z-10 h-[34rem] w-[34rem] rounded-full bg-violet-200/50 blur-3xl" />
        <div className="jf-container grid min-h-[590px] items-center gap-12 py-16 lg:grid-cols-[1.08fr_.92fr] lg:py-24">
          <div className="max-w-2xl">
            <div className="jf-eyebrow"><Sparkle /> Your next chapter starts here</div>
            <h1 className="mt-7 text-5xl font-black leading-[1.04] tracking-[-.065em] text-slate-950 sm:text-6xl lg:text-[4.45rem]">
              Work that moves you <span className="jf-gradient-text">forward.</span>
            </h1>
            <p className="mt-6 max-w-xl text-lg leading-8 text-slate-600">
              Find meaningful opportunities, connect with ambitious people, and take control of your career — all in one place.
            </p>
            <div className="mt-8 flex flex-col gap-3 sm:flex-row">
              <Link href="/jobs" className="jf-button jf-button-primary">Explore opportunities <Arrow /></Link>
              <Link href="/register" className="jf-button jf-button-secondary">Create your free account</Link>
            </div>
            <div className="mt-9 flex flex-wrap items-center gap-x-6 gap-y-3 text-sm text-slate-500">
              <span className="flex items-center gap-2"><span className="h-2 w-2 rounded-full bg-emerald-500"/> Discover roles that fit</span>
              <span className="flex items-center gap-2"><span className="h-2 w-2 rounded-full bg-violet-500"/> Track every application</span>
            </div>
          </div>

          <div className="relative mx-auto w-full max-w-[510px]">
            <div className="absolute -inset-5 rounded-[2.5rem] bg-gradient-to-br from-violet-200/70 via-fuchsia-100/60 to-indigo-100/80 blur-2xl"/>
            <div className="relative rounded-[1.8rem] border border-white/80 bg-white/90 p-4 shadow-[0_30px_90px_rgba(55,48,163,.16)] backdrop-blur-xl sm:p-6">
              <div className="flex items-center justify-between border-b border-slate-100 pb-4">
                <div><p className="text-xs font-semibold uppercase tracking-widest text-violet-600">Your career workspace</p><p className="mt-1 text-lg font-bold text-slate-900">A better way to move forward</p></div>
                <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-violet-50 text-violet-700"><Sparkle /></div>
              </div>
              <div className="mt-5 rounded-2xl bg-slate-950 p-5 text-white">
                <p className="text-xs font-semibold uppercase tracking-wider text-violet-200">Discover your next role</p>
                <p className="mt-2 text-2xl font-bold tracking-tight">Your skills have a place here.</p>
                <p className="mt-2 text-sm leading-6 text-slate-300">Search opportunities by what you know, where you want to work, and where you want to go.</p>
                <Link href="/jobs" className="mt-5 inline-flex items-center gap-2 rounded-lg bg-white px-4 py-2.5 text-sm font-bold text-slate-900 hover:bg-violet-50">Find opportunities <Arrow /></Link>
              </div>
              <div className="mt-4 grid grid-cols-2 gap-3">
                <div className="rounded-2xl border border-slate-100 bg-slate-50 p-4"><div className="flex h-9 w-9 items-center justify-center rounded-xl bg-emerald-100 text-emerald-700"><svg viewBox="0 0 24 24" className="h-5 w-5" fill="none"><path d="m5 12 4 4L19 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg></div><p className="mt-3 text-sm font-bold text-slate-900">Stay organised</p><p className="mt-1 text-xs leading-5 text-slate-500">Keep applications and interviews together.</p></div>
                <div className="rounded-2xl border border-slate-100 bg-slate-50 p-4"><div className="flex h-9 w-9 items-center justify-center rounded-xl bg-indigo-100 text-indigo-700"><Sparkle /></div><p className="mt-3 text-sm font-bold text-slate-900">Grow with intent</p><p className="mt-1 text-xs leading-5 text-slate-500">Build your profile and professional network.</p></div>
              </div>
            </div>
            <div className="absolute -bottom-6 -left-6 hidden rounded-2xl border border-white bg-white px-4 py-3 shadow-xl sm:block"><p className="text-xs font-semibold text-slate-500">Built around your journey</p><p className="mt-1 text-sm font-bold text-slate-900">Discover · Connect · Grow</p></div>
          </div>
        </div>
      </section>

      <section className="jf-container py-16 sm:py-20">
        <div className="max-w-2xl"><p className="text-xs font-bold uppercase tracking-[.18em] text-violet-600">Everything in one place</p><h2 className="jf-section-title mt-3">A career platform that works the way you do.</h2><p className="mt-4 leading-7 text-slate-600">From the first search to the final interview, keep your next move clear and organised.</p></div>
        <div className="mt-9 grid gap-4 md:grid-cols-3">
          {benefits.map((item) => <article key={item.number} className="jf-card jf-card-hover p-6"><p className="text-xs font-extrabold tracking-widest text-violet-600">{item.number}</p><h3 className="mt-5 text-lg font-bold tracking-tight">{item.title}</h3><p className="mt-2 text-sm leading-6 text-slate-600">{item.body}</p></article>)}
        </div>
      </section>

      <section id="opportunities" className="border-y border-slate-200 bg-white py-16 sm:py-20">
        <div className="jf-container">
          <div className="mb-8 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between"><div><p className="text-xs font-bold uppercase tracking-[.18em] text-violet-600">Explore jobs</p><h2 className="jf-section-title mt-3">Find the role that fits.</h2><p className="mt-3 text-slate-600">Search live opportunities and narrow down what matters to you.</p></div><Link href="/jobs" className="inline-flex items-center gap-2 text-sm font-bold text-violet-700 hover:text-violet-900">View all jobs <Arrow /></Link></div>
          <Suspense fallback={<div className="jf-card p-8 text-sm text-slate-500">Preparing job search…</div>}><JobSearch /></Suspense>
        </div>
      </section>
      <section className="jf-container py-16 sm:py-20">
        <div className="relative overflow-hidden rounded-[2rem] bg-slate-950 px-6 py-12 text-white sm:px-12 sm:py-14">
          <div className="absolute -right-20 -top-32 h-80 w-80 rounded-full bg-violet-600/30 blur-3xl"/>
          <div className="relative max-w-2xl"><p className="text-xs font-bold uppercase tracking-[.18em] text-violet-300">Ready when you are</p><h2 className="mt-4 text-3xl font-black tracking-[-.04em] sm:text-4xl">Your next opportunity is out there.</h2><p className="mt-4 leading-7 text-slate-300">Create your profile, explore roles and make your next career move with confidence.</p><Link href="/register" className="jf-button mt-7 bg-white text-slate-950 hover:bg-violet-50">Get started <Arrow /></Link></div>
        </div>
      </section>
    </div>
  );
}
