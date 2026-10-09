import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import { expect, test, vi } from 'vitest';
import { server, setupMockServer } from '../msw-node';
import { errorResponse, nextLinkMock, renderWithClient } from '../interview-test-utils';
import { setMockRole } from '@/mocks/handlers';

// vi.mock is hoisted, so the id of the upcoming mock interview is repeated literally here.
vi.mock('next/navigation', () => ({ useParams: () => ({ id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa' }) }));
vi.mock('next/link', () => ({ default: (props: Parameters<typeof nextLinkMock>[0]) => nextLinkMock(props) }));

import { InterviewDetailView } from '@/features/interviews/InterviewDetailView';

setupMockServer();

test('loading, then the seeker\'s own interview with its actions and a way back', async () => {
  renderWithClient(<InterviewDetailView role="JOB_SEEKER" />);
  expect(screen.getByRole('status')).toHaveTextContent(/loading interview/i);
  expect(await screen.findByRole('link', { name: 'Backend Engineer' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'All interviews' })).toHaveAttribute('href', '/interviews');
  expect(screen.getByRole('button', { name: 'Decline' })).toBeInTheDocument();
});

test('recruiters get the recruiter actions and their own back link', async () => {
  setMockRole('RECRUITER');
  renderWithClient(<InterviewDetailView role="RECRUITER" />);
  expect(await screen.findByRole('button', { name: 'Cancel interview' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'All interviews' })).toHaveAttribute('href', '/recruiter/interviews');
});

test('a missing or foreign interview is "not found" with no retry', async () => {
  server.use(http.get('/api/v1/interviews/:id', () => errorResponse(404, 'RESOURCE_NOT_FOUND', 'Interview not found.')));
  renderWithClient(<InterviewDetailView role="JOB_SEEKER" />);
  expect(await screen.findByRole('alert')).toHaveTextContent(/not found or unavailable/i);
  expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument();
});

test('a server error offers a retry that loads the interview', async () => {
  server.use(http.get('/api/v1/interviews/:id', () => errorResponse(503, 'AI_PROVIDER_UNAVAILABLE', 'Temporarily unavailable.'), { once: true }));
  renderWithClient(<InterviewDetailView role="JOB_SEEKER" />);
  expect(await screen.findByRole('alert')).toHaveTextContent('Temporarily unavailable.');
  await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
  expect(await screen.findByRole('link', { name: 'Backend Engineer' })).toBeInTheDocument();
});
