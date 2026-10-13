import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { expect, test, vi } from 'vitest';
import { server, setupMockServer } from '../msw-node';
import { errorResponse, inDays, renderWithClient, sampleInterview } from '../interview-test-utils';
import { setMockRole } from '@/mocks/handlers';

import { InterviewForm } from '@/features/interviews/InterviewForm';

setupMockServer();

const futureDate = () => inDays(10).slice(0, 10);

function scheduleForm() {
  const onDone = vi.fn();
  const onCancel = vi.fn();
  renderWithClient(<InterviewForm mode="schedule" applicationId="a-1" onDone={onDone} onCancel={onCancel} />);
  return { onDone, onCancel };
}

async function fillValidSchedule() {
  fireEvent.change(screen.getByLabelText('Date'), { target: { value: futureDate() } });
  fireEvent.change(screen.getByLabelText('Time'), { target: { value: '10:00' } });
  await userEvent.clear(screen.getByLabelText('Time zone'));
  await userEvent.type(screen.getByLabelText('Time zone'), 'Asia/Kolkata');
  await userEvent.type(screen.getByLabelText('Meeting link or address'), 'https://meet.example.com/x');
}

test('schedule: blocks submit and explains what is missing, without calling the API', async () => {
  setMockRole('RECRUITER');
  let calls = 0;
  server.use(http.post('/api/v1/applications/:id/interviews', () => { calls += 1; return errorResponse(500, 'X', 'should not be called'); }));
  const { onDone } = scheduleForm();
  await userEvent.click(screen.getByRole('button', { name: 'Schedule interview' }));
  expect(await screen.findByText('Add a meeting link or address')).toBeInTheDocument();
  expect(onDone).not.toHaveBeenCalled();
  expect(calls).toBe(0);
});

test('schedule: sends the contract payload (UTC instant, numeric duration, no empty notes) and closes', async () => {
  setMockRole('RECRUITER');
  let body: Record<string, unknown> = {};
  server.use(http.post('/api/v1/applications/:id/interviews', async ({ request }) => {
    body = (await request.json()) as Record<string, unknown>;
    return HttpResponse.json({ data: sampleInterview(), meta: {} }, { status: 201 });
  }));
  const { onDone } = scheduleForm();
  await fillValidSchedule();
  await userEvent.click(screen.getByRole('button', { name: 'Schedule interview' }));
  await waitFor(() => expect(onDone).toHaveBeenCalled());
  expect(body).toMatchObject({ type: 'VIDEO', durationMinutes: 45, timezone: 'Asia/Kolkata', locationOrLink: 'https://meet.example.com/x' });
  expect(body.scheduledAt).toBe(`${futureDate()}T04:30:00Z`); // 10:00 in Asia/Kolkata (UTC+05:30)
  expect(body).not.toHaveProperty('notes');
});

test('schedule: maps server field errors onto the fields and stays open', async () => {
  setMockRole('RECRUITER');
  server.use(http.post('/api/v1/applications/:id/interviews', () =>
    errorResponse(400, 'VALIDATION_FAILED', 'One or more fields are invalid.', [{ field: 'locationOrLink', code: 'INVALID', message: 'Not an allowed place' }])));
  const { onDone } = scheduleForm();
  await fillValidSchedule();
  await userEvent.click(screen.getByRole('button', { name: 'Schedule interview' }));
  expect(await screen.findByText('Not an allowed place')).toBeInTheDocument();
  expect(onDone).not.toHaveBeenCalled();
});

test('schedule: other failures (wrong application state, outage) show a message and allow another try', async () => {
  setMockRole('RECRUITER');
  server.use(http.post('/api/v1/applications/:id/interviews', () =>
    errorResponse(409, 'INVALID_STATE_TRANSITION', 'The application cannot move to INTERVIEW.'), { once: true }));
  const { onDone } = scheduleForm();
  await fillValidSchedule();
  await userEvent.click(screen.getByRole('button', { name: 'Schedule interview' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('The application cannot move to INTERVIEW.');
  expect(onDone).not.toHaveBeenCalled();
  expect(screen.getByRole('button', { name: 'Schedule interview' })).toBeEnabled();
});

test('schedule: Close calls onCancel', async () => {
  const { onCancel } = scheduleForm();
  await userEvent.click(screen.getByRole('button', { name: 'Close' }));
  expect(onCancel).toHaveBeenCalled();
});

test('reschedule: the form starts from the stored values and an unchanged save sends nothing', async () => {
  setMockRole('RECRUITER');
  let calls = 0;
  server.use(http.patch('/api/v1/interviews/:id', () => { calls += 1; return errorResponse(500, 'X', 'should not be called'); }));
  const onDone = vi.fn();
  renderWithClient(<InterviewForm mode="reschedule" interview={sampleInterview()} onDone={onDone} onCancel={vi.fn()} />);
  expect(screen.getByLabelText('Duration (minutes)')).toHaveValue('45');
  expect(screen.getByLabelText('Time zone')).toHaveValue('Asia/Kolkata');
  expect(screen.getByLabelText('Meeting link or address')).toHaveValue('https://meet.example.com/abc');
  await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));
  await waitFor(() => expect(onDone).toHaveBeenCalled());
  expect(calls).toBe(0);
});

test('reschedule: only changed fields are sent, and notes can be cleared with a blank value', async () => {
  setMockRole('RECRUITER');
  const bodies: Record<string, unknown>[] = [];
  const interview = sampleInterview();
  server.use(http.patch('/api/v1/interviews/:id', async ({ request }) => {
    bodies.push((await request.json()) as Record<string, unknown>);
    return HttpResponse.json({ data: interview, meta: {} });
  }));
  const onDone = vi.fn();
  renderWithClient(<InterviewForm mode="reschedule" interview={interview} onDone={onDone} onCancel={vi.fn()} />);
  await userEvent.clear(screen.getByLabelText('Duration (minutes)'));
  await userEvent.type(screen.getByLabelText('Duration (minutes)'), '60');
  await userEvent.clear(screen.getByLabelText(/internal notes/i));
  await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));
  await waitFor(() => expect(onDone).toHaveBeenCalled());
  expect(bodies).toEqual([{ durationMinutes: 60, notes: '' }]);
});

test('reschedule: a conflict from the server (interview already final) is shown', async () => {
  setMockRole('RECRUITER');
  server.use(http.patch('/api/v1/interviews/:id', () => errorResponse(409, 'INVALID_STATE_TRANSITION', 'An interview in status CANCELLED cannot be changed.')));
  const onDone = vi.fn();
  renderWithClient(<InterviewForm mode="reschedule" interview={sampleInterview()} onDone={onDone} onCancel={vi.fn()} />);
  await userEvent.clear(screen.getByLabelText('Duration (minutes)'));
  await userEvent.type(screen.getByLabelText('Duration (minutes)'), '90');
  await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));
  expect(await screen.findByRole('alert')).toHaveTextContent(/cannot be changed/i);
  expect(onDone).not.toHaveBeenCalled();
});

test('the time zone field suggests IANA zones but still validates free text', async () => {
  setMockRole('RECRUITER');
  scheduleForm();
  const input = screen.getByLabelText('Time zone');
  const list = document.getElementById(input.getAttribute('list') as string);
  expect(list?.querySelectorAll('option').length).toBeGreaterThan(5);
  await userEvent.clear(input);
  await userEvent.type(input, 'Mars/Olympus');
  await userEvent.click(screen.getByRole('button', { name: 'Schedule interview' }));
  expect(await screen.findByText(/IANA time zone/i)).toBeInTheDocument();
});
