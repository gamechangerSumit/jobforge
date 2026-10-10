import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, expect, test, vi } from 'vitest';
import { ApiClientError } from '@/lib/api/client';

const createReport = vi.fn();
vi.mock('@/lib/api/reports', () => ({
  createReport: (...a: unknown[]) => createReport(...a),
  listAdminReports: vi.fn(), getAdminReport: vi.fn(), resolveReport: vi.fn(),
}));

import { ReportForm } from '@/features/reports/ReportForm';

const err = (status: number, message: string) => new ApiClientError({ code: 'X', message } as never, status);

function renderForm(onDone = vi.fn(), onCancel = vi.fn()) {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  render(<QueryClientProvider client={client}><ReportForm targetType="JOB" targetId="job-1" onDone={onDone} onCancel={onCancel} /></QueryClientProvider>);
  return { onDone, onCancel };
}

beforeEach(() => createReport.mockReset());

test('requires a reason before calling the API', async () => {
  renderForm();
  await userEvent.click(screen.getByRole('button', { name: 'Send report' }));
  expect(await screen.findByText('Choose a reason')).toBeInTheDocument();
  expect(createReport).not.toHaveBeenCalled();
});

test('submits the contract body and omits blank details', async () => {
  createReport.mockResolvedValue({ id: 'r1' });
  const { onDone } = renderForm();
  await userEvent.selectOptions(screen.getByLabelText('Reason'), 'SCAM');
  await userEvent.click(screen.getByRole('button', { name: 'Send report' }));
  await waitFor(() => expect(onDone).toHaveBeenCalled());
  expect(createReport).toHaveBeenCalledWith({ targetType: 'JOB', targetId: 'job-1', reason: 'SCAM' });
});

test('omits details that are only NBSP, BOM or ideographic spaces', async () => {
  createReport.mockResolvedValue({ id: 'r1' });
  renderForm();
  await userEvent.selectOptions(screen.getByLabelText('Reason'), 'SPAM');
  await userEvent.click(screen.getByLabelText(/details/i));
  await userEvent.paste('\u00A0\uFEFF\u3000');
  await userEvent.click(screen.getByRole('button', { name: 'Send report' }));
  await waitFor(() => expect(createReport).toHaveBeenCalledWith({ targetType: 'JOB', targetId: 'job-1', reason: 'SPAM' }));
});

test('sends trimmed details when given', async () => {
  createReport.mockResolvedValue({ id: 'r1' });
  renderForm();
  await userEvent.selectOptions(screen.getByLabelText('Reason'), 'SPAM');
  await userEvent.type(screen.getByLabelText(/details/i), '  asks for a fee  ');
  await userEvent.click(screen.getByRole('button', { name: 'Send report' }));
  await waitFor(() => expect(createReport).toHaveBeenCalledWith({ targetType: 'JOB', targetId: 'job-1', reason: 'SPAM', details: 'asks for a fee' }));
});

test.each([
  [409, 'You already have an open report'],
  [404, 'no longer available'],
  [429, 'too quickly'],
])('shows a helpful message for HTTP %s and keeps the form open', async (status, text) => {
  createReport.mockRejectedValue(err(status, 'server text'));
  const { onDone } = renderForm();
  await userEvent.selectOptions(screen.getByLabelText('Reason'), 'SPAM');
  await userEvent.click(screen.getByRole('button', { name: 'Send report' }));
  expect(await screen.findByRole('alert')).toHaveTextContent(text);
  expect(onDone).not.toHaveBeenCalled();
});

test('422 shows the server explanation (e.g. reporting your own content)', async () => {
  createReport.mockRejectedValue(err(422, 'You cannot report your own content or account.'));
  renderForm();
  await userEvent.selectOptions(screen.getByLabelText('Reason'), 'OTHER');
  await userEvent.click(screen.getByRole('button', { name: 'Send report' }));
  expect(await screen.findByText('You cannot report your own content or account.')).toBeInTheDocument();
});
