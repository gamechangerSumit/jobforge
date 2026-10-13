import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import { expect, test, vi } from 'vitest';
import { server, setupMockServer } from '../msw-node';
import { emptyPage, errorResponse, nextLinkMock, renderWithClient } from '../interview-test-utils';
import { setMockRole } from '@/mocks/handlers';
import { ids } from '@/mocks/data';

vi.mock('next/link', () => ({ default: (props: Parameters<typeof nextLinkMock>[0]) => nextLinkMock(props) }));

import { ApplicationInterviewsPanel } from '@/features/interviews/ApplicationInterviewsPanel';

setupMockServer();

test('lists the application\'s interviews and opens the schedule form for a shortlisted candidate', async () => {
  setMockRole('RECRUITER');
  renderWithClient(<ApplicationInterviewsPanel applicationId={ids.application} status="SHORTLISTED" />);
  expect(screen.getByRole('status')).toHaveTextContent(/loading interviews/i);
  expect(await screen.findAllByRole('link', { name: 'Backend Engineer' })).toHaveLength(2);
  await userEvent.click(screen.getByRole('button', { name: 'Schedule interview' }));
  expect(screen.getByRole('form', { name: 'Schedule interview' })).toBeInTheDocument();
});

test('shows the empty state for an application that can take an interview', async () => {
  setMockRole('RECRUITER');
  server.use(http.get('/api/v1/interviews', () => emptyPage()));
  renderWithClient(<ApplicationInterviewsPanel applicationId={ids.application} status="INTERVIEW" />);
  expect(await screen.findByText(/no interviews scheduled yet/i)).toBeInTheDocument();
});

test('offers no scheduling for applications that cannot take an interview', async () => {
  setMockRole('RECRUITER');
  server.use(http.get('/api/v1/interviews', () => emptyPage()));
  renderWithClient(<ApplicationInterviewsPanel applicationId={ids.application} status="UNDER_REVIEW" />);
  expect(await screen.findByText(/shortlist the candidate to schedule an interview/i)).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Schedule interview' })).not.toBeInTheDocument();
});

test('a failed load can be retried', async () => {
  setMockRole('RECRUITER');
  server.use(http.get('/api/v1/interviews', () => errorResponse(500, 'INTERNAL_ERROR', 'Something went wrong.'), { once: true }));
  renderWithClient(<ApplicationInterviewsPanel applicationId={ids.application} status="SHORTLISTED" />);
  expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong.');
  await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
  expect(await screen.findAllByRole('link', { name: 'Backend Engineer' })).toHaveLength(2);
});
