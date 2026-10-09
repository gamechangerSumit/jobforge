import { expect, test } from 'vitest';
import type { EducationPatch, ExperiencePatch } from '@/lib/api/seeker';

// Compile-time contract: clearEndDate is accepted by both patch types (see SeekerAssetsRequests on the backend).
test('patch types accept clearEndDate', () => {
  const edu: EducationPatch = { clearEndDate: true };
  const exp: ExperiencePatch = { endDate: '2024-01-01', clearEndDate: false };
  expect(edu.clearEndDate).toBe(true);
  expect(exp.endDate).toBe('2024-01-01');
});
