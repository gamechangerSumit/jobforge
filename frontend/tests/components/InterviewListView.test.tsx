import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { expect, test, vi } from 'vitest';
import { server, setupMockServer } from '../msw-node';
import { emptyPage, errorResponse, nextLinkMock, renderWithClient } from '../interview-test-utils';
import { setMockRole } from '@/mocks/handlers';

vi.mock('next/link', () => ({ default: (props: Parameters<typeof nextLinkMock>[0]) => nextLinkMock(props) }));

import { InterviewListView } from '@/features/interviews/InterviewListView';

setupMockServer();

test('shows a loading state first, then the seeker\'s interviews without recruiter-only data', async () => {
  renderWithClient(<InterviewListView role="JOB_SEEKER" />);
  expect(screen.getByRole('status')).toHaveTextContent(/loading interviews/i);
  expect(await screen.findAllByRole('link', { name: 'Backend Engineer' })).toHaveLength(2);
  expect(screen.queryByText(/Panel: Priya/)).not.toBeInTheDocument();
  expect(screen.getAllByRole('button', { name: 'Confirm' })).toHaveLength(1); // only the upcoming one can still be answered
});

test('recruiters see the candidate, the answer status and internal notes', async () => {
  setMockRole('RECRUITER');
  renderWithClient(<InterviewListView role="RECRUITER" />);
  expect(await screen.findAllByText(/Candidate: Asha Kulkarni/)).toHaveLength(2);
  expect(screen.getByText('Panel: Priya')).toBeInTheDocument();
  expect(screen.getByText(/waiting for an answer/i)).toBeInTheDocument();
});

test('empty state differs by role', async () => {
  server.use(http.get('/api/v1/interviews', () => emptyPage()));
  const view = renderWithClient(<InterviewListView role="JOB_SEEKER" />);
  expect(await screen.findByTestId('interviews-empty')).toHaveTextContent(/no interviews yet/i);
  view.unmount();
  renderWithClient(<InterviewListView role="RECRUITER" />);
  expect(await screen.findByText(/schedule one from a shortlisted application/i)).toBeInTheDocument();
});

test('an error shows the server message and a retry that reloads the list', async () => {
  server.use(http.get('/api/v1/interviews', () => errorResponse(500, 'INTERNAL_ERROR', 'Something went wrong.'), { once: true }));
  renderWithClient(<InterviewListView role="JOB_SEEKER" />);
  expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong.');
  await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
  expect(await screen.findAllByRole('link', { name: 'Backend Engineer' })).toHaveLength(2);
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});

test('a network failure is reported too', async () => {
  server.use(http.get('/api/v1/interviews', () => HttpResponse.error(), { once: true }));
  renderWithClient(<InterviewListView role="JOB_SEEKER" />);
  expect(await screen.findByRole('alert')).toHaveTextContent(/cannot reach the server/i);
  expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
});

test('the status filter narrows the list through the API', async () => {
  renderWithClient(<InterviewListView role="JOB_SEEKER" />);
  await screen.findAllByRole('link', { name: 'Backend Engineer' });
  await userEvent.selectOptions(screen.getByLabelText('Status'), 'CONFIRMED');
  await waitFor(() => expect(screen.getAllByRole('link', { name: 'Backend Engineer' })).toHaveLength(1));
});
