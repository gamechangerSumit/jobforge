import { Card } from '@/components/ui/Card';

export function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return <Card><h2 className="mb-3 text-lg font-bold">{title}</h2>{children}</Card>;
}
