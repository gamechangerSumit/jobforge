'use client';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { login } from '@/lib/api/auth';
import { useSession } from '@/lib/auth/session';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Card } from '@/components/ui/Card';
const schema = z.object({ email: z.string().email(), password: z.string().min(10).max(64) });
type Form = z.infer<typeof schema>;
export default function LoginPage() { const router = useRouter(); const { refresh } = useSession(); const [error, setError] = useState(''); const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<Form>({ resolver: zodResolver(schema) }); return <div className="mx-auto max-w-md px-4 py-16"><Card><h1 className="text-2xl font-black">Sign in</h1><form className="mt-6 space-y-4" onSubmit={handleSubmit(async (v) => { setError(''); try { const s = await login(v.email, v.password); await refresh(); router.push(s.user.role === 'RECRUITER' ? '/recruiter/dashboard' : '/dashboard'); } catch { setError('Invalid credentials or sign-in failed.'); } })}><div><label className="mb-1 block text-sm font-semibold">Email</label><Input type="email" {...register('email')} />{errors.email && <p className="text-sm text-red-600">{errors.email.message}</p>}</div><div><label className="mb-1 block text-sm font-semibold">Password</label><Input type="password" {...register('password')} />{errors.password && <p className="text-sm text-red-600">{errors.password.message}</p>}</div>{error && <p role="alert" className="text-sm text-red-600">{error}</p>}<Button disabled={isSubmitting} className="w-full">{isSubmitting ? 'Signing in…' : 'Sign in'}</Button></form></Card></div>; }
