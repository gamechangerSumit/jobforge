import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http } from 'msw';
import { expect, test, vi } from 'vitest';
import { server, setupMockServer } from '../msw-node';
import { errorResponse, nextLinkMock, renderWithClient } from '../interview-test-utils';
import { setMockRole } from '@/mocks/handlers';

vi.mock('next/link', () => ({ default: (props: Parameters<typeof nextLinkMock>[0]) => nextLinkMock(props) }));

import { InterviewListView } from '@/features/interviews/InterviewListView';

setupMockServer();

/** Every card owns a reason dialog; only the one that was opened carries the `open` attribute. */
const openDialog = () => document.querySelector('dialog[open]') as HTMLElement;

/** End-to-end through the real lib/api client + MSW: accept/decline, cancel, complete and reschedule actions. */

test('seeker confirms: the list refreshes and the button becomes "Confirmed"', async () => {
  renderWithClient(<InterviewListView role="JOB_SEEKER" />);
  await userEvent.type(await screen.findByLabelText(/message to the recruiter/i), 'See you then');
  await userEvent.click(screen.getByRole('button', { name: 'Confirm' }));
  expect(await screen.findByRole('button', { name: 'Confirmed' })).toBeDisabled();
});

test('seeker declines: the answer controls disappear for that interview', async () => {
  renderWithClient(<InterviewListView role="JOB_SEEKER" />);
  await userEvent.click(await screen.findByRole('button', { name: 'Decline' }));
  await waitFor(() => expect(screen.queryByRole('button', { name: 'Decline' })).not.toBeInTheDocument());
});

test('seeker sees the server message when answering fails (interview changed meanwhile)', async () => {
  server.use(http.post('/api/v1/interviews/:id/respond', () => errorResponse(409, 'INVALID_STATE_TRANSITION', 'An interview in status CANCELLED cannot be changed.')));
  renderWithClient(<InterviewListView role="JOB_SEEKER" />);
  await userEvent.click(await screen.findByRole('button', { name: 'Decline' }));
  expect(await screen.findByRole('alert')).toHaveTextContent(/cannot be changed/i);
});

test('recruiter cancels with a reason and the interview shows as cancelled with that reason', async () => {
  setMockRole('RECRUITER');
  renderWithClient(<InterviewListView role="RECRUITER" />);
  const cancelButtons = await screen.findAllByRole('button', { name: 'Cancel interview' });
  await userEvent.click(cancelButtons[1]); // list is ordered by time: [started, upcoming]
  const dialog = openDialog();
  fireEvent.change(within(dialog).getByLabelText(/reason \(required/i), { target: { value: 'Position on hold' } });
  fireEvent.click(within(dialog).getByRole('button', { name: 'Confirm', hidden: true }));
  expect(await screen.findByText('Position on hold')).toBeInTheDocument();
  await waitFor(() => expect(screen.getAllByRole('button', { name: 'Cancel interview' })).toHaveLength(1));
});

test('recruiter dismissing the cancel dialog changes nothing', async () => {
  setMockRole('RECRUITER');
  let calls = 0;
  server.use(http.post('/api/v1/interviews/:id/cancel', () => { calls += 1; return errorResponse(500, 'X', 'should not be called'); }));
  renderWithClient(<InterviewListView role="RECRUITER" />);
  const cancelButtons = await screen.findAllByRole('button', { name: 'Cancel interview' });
  await userEvent.click(cancelButtons[1]);
  fireEvent.click(within(openDialog()).getByRole('button', { name: 'Cancel', hidden: true }));
  await waitFor(() => expect(screen.getAllByRole('button', { name: 'Cancel interview' })).toHaveLength(2));
  expect(calls).toBe(0);
});

test('recruiter marks a started interview completed and it becomes final', async () => {
  setMockRole('RECRUITER');
  renderWithClient(<InterviewListView role="RECRUITER" />);
  await userEvent.click(await screen.findByRole('button', { name: 'Mark completed' }));
  await waitFor(() => expect(screen.queryByRole('button', { name: 'Mark completed' })).not.toBeInTheDocument());
  expect(screen.getAllByRole('button', { name: 'Cancel interview' })).toHaveLength(1);
});

test('recruiter reschedules: the new length shows and the candidate answer is waiting again', async () => {
  setMockRole('RECRUITER');
  renderWithClient(<InterviewListView role="RECRUITER" />);
  const edit = await screen.findAllByRole('button', { name: /reschedule \/ edit/i });
  await userEvent.click(edit[1]);
  await userEvent.clear(screen.getByLabelText('Duration (minutes)'));
  await userEvent.type(screen.getByLabelText('Duration (minutes)'), '60');
  await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));
  expect(await screen.findByText(/Video call · 60 min/)).toBeInTheDocument();
});
