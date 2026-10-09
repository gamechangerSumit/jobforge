import { RoleGuard } from '@/components/layout/RoleGuard';
export default function SeekerLayout({ children }: { children: React.ReactNode }) { return <RoleGuard role="JOB_SEEKER">{children}</RoleGuard>; }
