import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, expect, test, vi } from 'vitest';
import type { Interview } from '@/types/interviews';

const respond = vi.fn();
vi.mock('@/lib/api/interviews', () => ({
  respondToInterview: (...a: unknown[]) => respond(...a),
  cancelInterview: vi.fn(), completeInterview: vi.fn(), updateInterview: vi.fn(), scheduleInterview: vi.fn(),
  listInterviews: vi.fn(), getInterview: vi.fn(),
}));

import { InterviewCard } from '@/features/interviews/InterviewCard';

const base: Interview = {
  id: 'i1', applicationId: 'a1', applicationStatus: 'INTERVIEW',
  job: { id: 'j1', title: 'Backend Engineer', company: { id: 'c1', name: 'Acme', slug: 'acme', verified: true } },
  scheduledBy: { id: 'r1', handle: 'rec', firstName: 'Rhea', lastName: 'Rao' },
  type: 'VIDEO', scheduledAt: new Date(Date.now() + 3 * 86_400_000).toISOString(), durationMinutes: 45, timezone: 'Asia/Kolkata',
  locationOrLink: 'https://meet.example.com/x', status: 'SCHEDULED', seekerResponse: 'PENDING', createdAt: '', updatedAt: '',
} as Interview;

function renderCard(interview: Interview, role: 'RECRUITER' | 'JOB_SEEKER') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={client}><InterviewCard interview={interview} role={role} /></QueryClientProvider>);
}

beforeEach(() => respond.mockReset());

test('seeker can confirm with an optional note', async () => {
  respond.mockResolvedValue({ ...base, status: 'CONFIRMED', seekerResponse: 'CONFIRMED' });
  renderCard(base, 'JOB_SEEKER');
  await userEvent.type(screen.getByLabelText(/message to the recruiter/i), 'See you then');
  await userEvent.click(screen.getByRole('button', { name: 'Confirm' }));
  await waitFor(() => expect(respond).toHaveBeenCalledWith('i1', { response: 'CONFIRM', note: 'See you then' }));
});

test('seeker can decline', async () => {
  respond.mockResolvedValue({ ...base, status: 'DECLINED', seekerResponse: 'DECLINED' });
  renderCard(base, 'JOB_SEEKER');
  await userEvent.click(screen.getByRole('button', { name: 'Decline' }));
  await waitFor(() => expect(respond).toHaveBeenCalledWith('i1', { response: 'DECLINE' }));
});

test('seeker sees no actions for cancelled, started or closed-application interviews and never sees notes', () => {
  const { unmount } = renderCard({ ...base, status: 'CANCELLED', cancelledReason: 'Position filled' }, 'JOB_SEEKER');
  expect(screen.queryByRole('button', { name: 'Confirm' })).not.toBeInTheDocument();
  expect(screen.getByText('Position filled')).toBeInTheDocument();
  unmount();
  const started = renderCard({ ...base, scheduledAt: new Date(Date.now() - 3_600_000).toISOString() }, 'JOB_SEEKER');
  expect(screen.queryByRole('button', { name: 'Decline' })).not.toBeInTheDocument();
  started.unmount();
  renderCard({ ...base, applicationStatus: 'WITHDRAWN', notes: 'secret' }, 'JOB_SEEKER');
  expect(screen.queryByRole('button', { name: 'Confirm' })).not.toBeInTheDocument();
  expect(screen.queryByText('secret')).not.toBeInTheDocument();
});

test('recruiter sees reschedule, cancel and the candidate answer; completion only after the start', () => {
  const { unmount } = renderCard({ ...base, seeker: { id: 's1', handle: 'sam', firstName: 'Sam', lastName: 'Lee' }, notes: 'Panel: Priya' }, 'RECRUITER');
  expect(screen.getByRole('button', { name: /reschedule/i })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Cancel interview' })).toBeInTheDocument();
  expect(screen.getByText('Panel: Priya')).toBeInTheDocument();
  expect(screen.getByText(/waiting for an answer/i)).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Mark completed' })).not.toBeInTheDocument();
  unmount();
  renderCard({ ...base, status: 'CONFIRMED', seekerResponse: 'CONFIRMED', scheduledAt: new Date(Date.now() - 3_600_000).toISOString() }, 'RECRUITER');
  expect(screen.getByRole('button', { name: 'Mark completed' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Mark no-show' })).toBeInTheDocument();
});

test('recruiter gets no actions on final interviews, and only http(s) places are links', () => {
  renderCard({ ...base, status: 'COMPLETED', locationOrLink: 'javascript:alert(1)' }, 'RECRUITER');
  expect(screen.queryByRole('button', { name: 'Cancel interview' })).not.toBeInTheDocument();
  expect(screen.queryByRole('link', { name: /javascript/i })).not.toBeInTheDocument();
});

test('seeker gets a plain-language note instead of buttons when nothing can be answered', () => {
  const cancelled = renderCard({ ...base, status: 'CANCELLED', cancelledReason: 'Position filled' }, 'JOB_SEEKER');
  expect(screen.getByTestId('seeker-notice')).toHaveTextContent(/was cancelled/i);
  cancelled.unmount();
  const declined = renderCard({ ...base, status: 'DECLINED', seekerResponse: 'DECLINED' }, 'JOB_SEEKER');
  expect(screen.getByTestId('seeker-notice')).toHaveTextContent(/you declined this interview/i);
  declined.unmount();
  const started = renderCard({ ...base, scheduledAt: new Date(Date.now() - 3_600_000).toISOString() }, 'JOB_SEEKER');
  expect(screen.getByTestId('seeker-notice')).toHaveTextContent(/already started/i);
  started.unmount();
  const done = renderCard({ ...base, status: 'COMPLETED' }, 'JOB_SEEKER');
  expect(screen.getByTestId('seeker-notice')).toHaveTextContent(/is over/i);
  done.unmount();
  renderCard(base, 'JOB_SEEKER');
  expect(screen.queryByTestId('seeker-notice')).not.toBeInTheDocument();
});
