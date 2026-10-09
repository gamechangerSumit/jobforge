'use client';
import { CompletenessCard } from '@/features/profile/CompletenessCard';
import { HistorySections } from '@/features/profile/HistorySections';
import { ProfileForm } from '@/features/profile/ProfileForm';
import { ResumesSection } from '@/features/profile/ResumesSection';
import { SkillsSection } from '@/features/profile/SkillsSection';

export default function ProfilePage() {
  return (
    <div className="mx-auto max-w-3xl space-y-6 px-4 py-10">
      <h1 className="text-3xl font-black">My profile</h1>
      <CompletenessCard />
      <ProfileForm />
      <ResumesSection />
      <SkillsSection />
      <HistorySections />
    </div>
  );
}
