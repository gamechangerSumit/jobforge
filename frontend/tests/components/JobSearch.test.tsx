import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { vi } from 'vitest';
import { JobSearch } from '@/features/jobs/JobSearch';

vi.mock('next/navigation', () => ({
  usePathname: () => '/jobs',
  useRouter: () => ({ replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock('@/lib/api/jobs', () => ({
  listJobsPage: vi.fn().mockResolvedValue({ items: [], page: { number: 0, size: 12, totalElements: 0, totalPages: 0, hasNext: false } }),
}));

function renderWithQueryClient(ui: React.ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      {ui}
    </QueryClientProvider>,
  );
}

test('renders the Phase 2 job discovery filters', () => {
  renderWithQueryClient(<JobSearch />);

  expect(screen.getByLabelText('Keywords')).toBeInTheDocument();
  expect(screen.getByLabelText('Location')).toBeInTheDocument();
  expect(screen.getByLabelText('Skills')).toBeInTheDocument();
  expect(screen.getByLabelText('Work mode')).toBeInTheDocument();
  expect(screen.getByLabelText('Employment')).toBeInTheDocument();
  expect(screen.getByLabelText('Experience')).toBeInTheDocument();
  expect(screen.getByLabelText('Posted')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Search jobs' })).toBeInTheDocument();
});