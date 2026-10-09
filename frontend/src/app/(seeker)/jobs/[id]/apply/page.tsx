'use client';
import { useRouter, useParams } from 'next/navigation'; import { ApplyForm } from '@/features/applications/ApplyForm';
export default function ApplyPage() { const router = useRouter(); const params = useParams<{ id: string }>(); return <div className="mx-auto max-w-3xl px-4 py-10"><ApplyForm jobId={params.id} onSuccess={(id) => router.push(`/applications/${id}`)} /></div>; }
