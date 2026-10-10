import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, expect, test, vi } from 'vitest';
import { ApiClientError } from '@/lib/api/client';
import type { AdminReportDetail } from '@/types/reports';

const resolveReport = vi.fn();

vi.mock('@/lib/api/reports', () => ({
  resolveReport: (...a: unknown[]) => resolveReport(...a),
  createReport: vi.fn(),
  listAdminReports: vi.fn(),
  getAdminReport: vi.fn(),
}));

import { ResolvePanel } from '@/features/reports/ResolvePanel';

const base: AdminReportDetail = {
  id: 'r1',
  targetType: 'JOB',
  targetId: 'j1',
  reason: 'SCAM',
  status: 'OPEN',
  reporter: { id: 'u1', displayName: 'Sam Lee' },
  target: {
    type: 'JOB',
    id: 'j1',
    label: 'Backend Engineer',
    status: 'PUBLISHED',
    available: true,
  },
  actions: [],
  createdAt: '2026-10-01T10:00:00Z',
  updatedAt: '2026-10-01T10:00:00Z',
};

const reason = 'Clear evidence of a fee scam.';

function renderPanel(report: AdminReportDetail) {
  const client = new QueryClient({
    defaultOptions: {
      mutations: { retry: false },
    },
  });

  render(
    <QueryClientProvider client={client}>
      <ResolvePanel report={report} />
    </QueryClientProvider>,
  );
}

const options = () =>
  screen
    .getAllByRole('option')
    .map((option) => (option as HTMLOptionElement).value)
    .filter(Boolean);

beforeEach(() => {
  resolveReport.mockReset();
});

test('offers only the decisions the backend supports per target type', () => {
  renderPanel(base);

  expect(options()).toEqual([
    'DISMISS',
    'WARN_USER',
    'REMOVE_CONTENT',
  ]);
});

test('user reports can be suspended, never "removed"', () => {
  renderPanel({
    ...base,
    targetType: 'USER',
    target: { ...base.target, type: 'USER' },
  });

  expect(options()).toEqual([
    'DISMISS',
    'WARN_USER',
    'SUSPEND_USER',
  ]);
});

test('a vanished target can only be dismissed', () => {
  renderPanel({
    ...base,
    target: { type: 'JOB', id: 'j1', available: false },
  });

  expect(options()).toEqual(['DISMISS']);
  expect(screen.getByText(/no longer exists/i)).toBeInTheDocument();
});

test('closed reports show no form', () => {
  renderPanel({ ...base, status: 'RESOLVED' });

  expect(
    screen.queryByRole('button', { name: 'Save decision' }),
  ).not.toBeInTheDocument();

  expect(screen.getByText(/report is closed/i)).toBeInTheDocument();
});

test('validates the 10-character reason and requires confirmation for destructive actions', async () => {
  renderPanel(base);

  await userEvent.selectOptions(
    screen.getByLabelText('Decision'),
    'REMOVE_CONTENT',
  );
  await userEvent.type(screen.getByLabelText(/reason/i), 'short');
  await userEvent.click(
    screen.getByRole('button', { name: 'Save decision' }),
  );

  expect(
    await screen.findByText(/at least 10 characters/i),
  ).toBeInTheDocument();

  expect(
    screen.getByText(/Confirm that you want to apply/i),
  ).toBeInTheDocument();

  expect(resolveReport).not.toHaveBeenCalled();
});

test('saves a harmless decision with the contract body', async () => {
  resolveReport.mockResolvedValue({ ...base, status: 'DISMISSED' });

  renderPanel(base);

  await userEvent.selectOptions(
    screen.getByLabelText('Decision'),
    'DISMISS',
  );
  await userEvent.type(screen.getByLabelText(/reason/i), reason);
  await userEvent.click(
    screen.getByRole('button', { name: 'Save decision' }),
  );

  await waitFor(() =>
    expect(resolveReport).toHaveBeenCalledWith('r1', {
      action: 'DISMISS',
      reason,
    }),
  );

  expect(await screen.findByRole('status')).toHaveTextContent(
    'Decision saved',
  );
});

test('a destructive decision goes through once confirmed', async () => {
  resolveReport.mockResolvedValue({ ...base, status: 'RESOLVED' });

  renderPanel(base);

  await userEvent.selectOptions(
    screen.getByLabelText('Decision'),
    'REMOVE_CONTENT',
  );
  await userEvent.type(screen.getByLabelText(/reason/i), reason);
  await userEvent.click(screen.getByRole('checkbox'));
  await userEvent.click(
    screen.getByRole('button', { name: 'Save decision' }),
  );

  await waitFor(() =>
    expect(resolveReport).toHaveBeenCalledWith('r1', {
      action: 'REMOVE_CONTENT',
      reason,
    }),
  );
});

test.each([
  [409, 'already closed'],
  [403, 'do not have permission'],
  [422, 'Not supported here.'],
])(
  'maps HTTP %s from the resolve call to a clear message',
  async (status, expectedText) => {
    resolveReport.mockRejectedValue(
      new ApiClientError(
        { code: 'X', message: 'Not supported here.' } as never,
        status,
      ),
    );

    renderPanel(base);

    await userEvent.selectOptions(
      screen.getByLabelText('Decision'),
      'DISMISS',
    );
    await userEvent.type(screen.getByLabelText(/reason/i), reason);
    await userEvent.click(
      screen.getByRole('button', { name: 'Save decision' }),
    );

    const alert = await screen.findByRole('alert');

    console.log('[TEST] HTTP status:', status);
    console.log('[TEST] Expected:', expectedText);
    console.log('[TEST] Actual:', alert.textContent);
    console.log('[TEST] HTML:', alert.outerHTML);

    expect(alert).toHaveTextContent(expectedText);
  },
);

test('accepts 10 emoji (10 code points, 20 UTF-16 units) and sends them untouched', async () => {
  const tenEmoji = '\u{1F600}'.repeat(10);

  resolveReport.mockResolvedValue({ ...base, status: 'DISMISSED' });

  renderPanel(base);

  await userEvent.selectOptions(
    screen.getByLabelText('Decision'),
    'DISMISS',
  );
  await userEvent.click(screen.getByLabelText(/reason/i));
  await userEvent.paste(tenEmoji);
  await userEvent.click(
    screen.getByRole('button', { name: 'Save decision' }),
  );

  await waitFor(() =>
    expect(resolveReport).toHaveBeenCalledWith('r1', {
      action: 'DISMISS',
      reason: tenEmoji,
    }),
  );
});

test('rejects 9 emoji and does not cap the textarea at 500 UTF-16 units', async () => {
  renderPanel(base);

  await userEvent.selectOptions(
    screen.getByLabelText('Decision'),
    'DISMISS',
  );

  const box = screen.getByLabelText(/reason/i);

  await userEvent.click(box);
  await userEvent.paste('\u{1F600}'.repeat(9));
  await userEvent.click(
    screen.getByRole('button', { name: 'Save decision' }),
  );

  expect(
    await screen.findByText(/at least 10 characters/i),
  ).toBeInTheDocument();

  expect(resolveReport).not.toHaveBeenCalled();

  await userEvent.clear(box);
  await userEvent.click(box);
  await userEvent.paste('\u{1F600}'.repeat(300));

  expect((box as HTMLTextAreaElement).value).toBe(
    '\u{1F600}'.repeat(300),
  );
});