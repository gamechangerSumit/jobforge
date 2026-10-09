import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { expect, test, vi } from 'vitest';
import { useReasonDialog } from '@/components/ui/ReasonDialog';

function Harness({ onResult, min = 1 }: { onResult: (v: string | null) => void; min?: number }) {
  const { ask, dialog } = useReasonDialog();
  return (
    <div>
      {dialog}
      <button onClick={async () => onResult(await ask('Rejection reason', min))}>open</button>
    </div>
  );
}

test('resolves with the trimmed reason on confirm', async () => {
  const onResult = vi.fn();
  render(<Harness onResult={onResult} />);
  fireEvent.click(screen.getByText('open'));
  fireEvent.change(screen.getByLabelText(/reason \(required/i), { target: { value: '  not a real company  ' } });
  fireEvent.click(screen.getByText('Confirm'));
  await waitFor(() => expect(onResult).toHaveBeenCalledWith('not a real company'));
});

test('resolves with null when cancelled', async () => {
  const onResult = vi.fn();
  render(<Harness onResult={onResult} />);
  fireEvent.click(screen.getByText('open'));
  fireEvent.click(screen.getByText('Cancel'));
  await waitFor(() => expect(onResult).toHaveBeenCalledWith(null));
});

test('enforces the minimum length with an accessible error', async () => {
  const onResult = vi.fn();
  render(<Harness onResult={onResult} min={10} />);
  fireEvent.click(screen.getByText('open'));
  fireEvent.change(screen.getByLabelText(/reason \(required/i), { target: { value: 'short' } });
  fireEvent.click(screen.getByText('Confirm'));
  expect((await screen.findByRole('alert')).textContent).toMatch(/at least 10/);
  expect(onResult).not.toHaveBeenCalled();
});
