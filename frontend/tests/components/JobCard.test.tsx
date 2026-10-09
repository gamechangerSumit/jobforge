import { vi } from 'vitest';

vi.mock('@/lib/api/auth', () => ({
  me: vi.fn().mockRejectedValue(new Error('anonymous')),
  logout: vi.fn(),
}));

import { render, screen } from '@testing-library/react';

import { JobCard } from '@/components/jobs/JobCard';
import { SessionProvider } from '@/lib/auth/session';

const job = {
  id: '1',
  title: 'Backend Engineer',

  company: {
    id: 'c',
    name: 'Acme',
    slug: 'acme',
    logoUrl: null,
    verified: true,
  },

  location: {
    city: 'Nagpur',
    state: 'Maharashtra',
    country: 'IN',
  },

  workMode: 'HYBRID' as const,
  employmentType: 'FULL_TIME' as const,
  experienceLevel: 'MID' as const,

  salary: null,

  skills: ['java'],

  postedAt: '2026-01-01T00:00:00Z',

  saved: false,
  applied: false,
};

test('renders a job summary and save control', async () => {
  render(
    <SessionProvider>
      <JobCard job={job} />
    </SessionProvider>,
  );

  expect(
    screen.getByRole('link', {
      name: 'Backend Engineer',
    }),
  ).toBeInTheDocument();

  expect(
    await screen.findByRole('link', {
      name: 'Sign in to save',
    }),
  ).toBeInTheDocument();
});