import { RoleGuard } from '@/components/layout/RoleGuard';
export default function RecruiterLayout({ children }: { children: React.ReactNode }) { return <RoleGuard role="RECRUITER">{children}</RoleGuard>; }
