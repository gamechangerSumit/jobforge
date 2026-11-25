'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiClientError } from '@/lib/api/client';
import { getAiAdminUsage, getAiQuotaPolicies, updateAiQuotaPolicy, type AiQuotaPolicy } from '@/lib/api/ai';
import { useSession } from '@/lib/auth/session';

const roles: AiQuotaPolicy['role'][] = ['JOB_SEEKER', 'RECRUITER', 'ADMIN'];
const periods: AiQuotaPolicy['period'][] = ['DAILY', 'MONTHLY'];

export default function AdminAiPage() {
  const { user, loading } = useSession();
  const client = useQueryClient();
  const policies = useQuery({ queryKey: ['admin-ai-policies'], queryFn: getAiQuotaPolicies, enabled: user?.role === 'ADMIN' });
  const usage = useQuery({ queryKey: ['admin-ai-usage'], queryFn: getAiAdminUsage, enabled: user?.role === 'ADMIN' });
  const [drafts, setDrafts] = useState<AiQuotaPolicy[]>([]);
  const [saved, setSaved] = useState<string | null>(null);
  useEffect(() => { if (policies.data) setDrafts(policies.data); }, [policies.data]);
  const update = useMutation({
    mutationFn: updateAiQuotaPolicy,
    onSuccess: (data) => { setSaved('Policy saved'); void client.invalidateQueries({ queryKey: ['admin-ai-policies'] }); },
  });
  const change = (key: string, patch: Partial<AiQuotaPolicy>) => setDrafts((current) => current.map((item) => `${item.role}:${item.period}` === key ? { ...item, ...patch } : item));
  const save = (policy: AiQuotaPolicy) => { setSaved(null); update.mutate(policy); };
  const error = update.error instanceof ApiClientError ? update.error.message : update.error?.message;

  if (loading) return <div className="jf-container py-16 text-sm text-slate-500">Loading administrator workspace…</div>;
  if (!user || user.role !== 'ADMIN') return <div className="jf-container py-16"><div className="jf-card mx-auto max-w-lg p-8 text-center"><h1 className="text-2xl font-black">Administrator access required</h1><p className="mt-2 text-sm text-slate-600">This area is only available to JobForge administrators.</p><Link href="/login" className="jf-button jf-button-primary mt-5">Sign in</Link></div></div>;

  return <div className="min-h-screen bg-[#f6f7fb] pb-16">
    <div className="border-b border-slate-200 bg-white"><div className="jf-container py-9"><Link href="/admin/analytics" className="text-sm font-semibold text-violet-700">← Admin workspace</Link><p className="mt-5 text-xs font-bold uppercase tracking-[.18em] text-violet-600">Platform controls</p><h1 className="mt-2 text-4xl font-black tracking-[-.05em] text-slate-950">AI usage & quotas</h1><p className="mt-3 max-w-2xl text-sm leading-6 text-slate-600">Manage daily and monthly AI credit budgets by role. Changes apply to new reservations and do not rewrite past usage.</p></div></div>
    <div className="jf-container mt-8 space-y-6">
      <section className="grid gap-4 sm:grid-cols-3">
        <div className="jf-card p-5"><p className="text-xs font-bold uppercase tracking-wider text-slate-500">Tracked operations</p><p className="mt-2 text-3xl font-black">{usage.data?.length ?? '—'}</p></div>
        <div className="jf-card p-5"><p className="text-xs font-bold uppercase tracking-wider text-slate-500">AI requests (last 30 days)</p><p className="mt-2 text-3xl font-black">{usage.data?.reduce((sum, item) => sum + item.requestCount, 0).toLocaleString() ?? '—'}</p></div>
        <div className="jf-card p-5"><p className="text-xs font-bold uppercase tracking-wider text-slate-500">Credits used (last 30 days)</p><p className="mt-2 text-3xl font-black">{usage.data?.reduce((sum, item) => sum + item.creditsUsed, 0).toLocaleString() ?? '—'}</p></div>
      </section>

      <section className="jf-card overflow-hidden">
        <div className="border-b border-slate-100 p-5 sm:p-6"><h2 className="text-lg font-extrabold">Quota policies</h2><p className="mt-1 text-sm text-slate-500">Configure credit limits and per-minute request limits for each role.</p></div>
        {policies.isLoading ? <p className="p-8 text-sm text-slate-500">Loading quota policies…</p> : policies.isError ? <div role="alert" className="p-6 text-sm text-rose-700">Could not load quota policies. <button className="font-bold underline" onClick={() => void policies.refetch()}>Try again</button></div> : <div className="divide-y divide-slate-100">
          {roles.map((role) => <div key={role} className="p-5 sm:p-6"><div className="mb-4 flex items-center justify-between"><h3 className="font-extrabold">{role.replaceAll('_',' ')}</h3><span className="rounded-full bg-slate-100 px-2.5 py-1 text-[11px] font-bold text-slate-600">Role policy</span></div><div className="grid gap-3 lg:grid-cols-2">
            {periods.map((period) => {
              const key = `${role}:${period}`;
              const policy = drafts.find((item) => `${item.role}:${item.period}` === key);
              if (!policy) return <div key={key} className="rounded-xl border border-dashed border-slate-200 p-4 text-sm text-slate-400">{period}: policy not configured</div>;
              return <div key={key} className="rounded-xl border border-slate-200 bg-slate-50/70 p-4">
                <div className="mb-3 flex items-center justify-between"><p className="text-xs font-extrabold uppercase tracking-wider text-slate-500">{period} budget</p><label className="flex items-center gap-2 text-xs font-semibold text-slate-600"><input type="checkbox" checked={policy.active} onChange={(e) => change(key,{active:e.target.checked})}/> Active</label></div>
                <div className="grid grid-cols-2 gap-3"><label className="text-xs font-semibold text-slate-600">Credits<input className="jf-input mt-1" type="number" min="0" value={policy.creditLimit} onChange={(e) => change(key,{creditLimit:Math.max(0,Number(e.target.value)||0)})}/></label><label className="text-xs font-semibold text-slate-600">Requests / minute<input className="jf-input mt-1" type="number" min="1" value={policy.rateLimitPerMinute} onChange={(e) => change(key,{rateLimitPerMinute:Math.max(1,Number(e.target.value)||1)})}/></label></div>
                <div className="mt-3 flex justify-end"><button type="button" onClick={() => save(policy)} disabled={update.isPending} className="jf-button jf-button-secondary !px-3 !py-2 !text-xs disabled:opacity-50">Save policy</button></div>
              </div>;
            })}
          </div></div>)}
        </div>}
        {error && <p role="alert" className="border-t border-rose-100 bg-rose-50 p-4 text-sm text-rose-700">{error}</p>}
        {saved && <p role="status" className="border-t border-emerald-100 bg-emerald-50 p-4 text-sm font-semibold text-emerald-700">{saved}</p>}
      </section>

      <section className="jf-card overflow-hidden"><div className="border-b border-slate-100 p-5 sm:p-6"><h2 className="text-lg font-extrabold">AI activity</h2><p className="mt-1 text-sm text-slate-500">Aggregated usage over the last 30 days.</p></div>{usage.isLoading ? <p className="p-6 text-sm text-slate-500">Loading activity…</p> : usage.isError ? <p className="p-6 text-sm text-rose-700">Activity data is unavailable.</p> : <div className="overflow-x-auto"><table className="w-full min-w-[560px] text-left text-sm"><thead className="bg-slate-50 text-xs uppercase tracking-wider text-slate-500"><tr><th className="p-4">Operation</th><th className="p-4">Requests</th><th className="p-4">Credits used</th><th className="p-4">Released</th></tr></thead><tbody className="divide-y divide-slate-100">{usage.data?.map((item) => <tr key={item.operation}><td className="p-4 font-semibold text-slate-800">{item.operation.replaceAll('_',' ')}</td><td className="p-4 tabular-nums">{item.requestCount.toLocaleString()}</td><td className="p-4 tabular-nums">{item.creditsUsed.toLocaleString()}</td><td className="p-4 tabular-nums">{item.releasedCount.toLocaleString()}</td></tr>)}</tbody></table>{usage.data?.length === 0 && <p className="p-6 text-center text-sm text-slate-500">No AI usage recorded yet.</p>}</div>}</section>
    </div>
  </div>;
}
