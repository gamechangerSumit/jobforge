'use client';
import { useParams } from 'next/navigation';
import { JobAnalyticsView } from '@/features/analytics/JobAnalyticsView';

export default function Page() {
  const { jobId } = useParams<{ jobId: string }>();
  return <JobAnalyticsView jobId={jobId} />;
}
