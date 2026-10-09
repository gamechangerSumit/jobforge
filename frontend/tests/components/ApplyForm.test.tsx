import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, test, vi } from 'vitest';

const listResumes = vi.fn();
vi.mock('@/lib/api/applications', () => ({ listResumes: () => listResumes(), applyToJob: vi.fn() }));

import { ApplyForm } from '@/features/applications/ApplyForm';

beforeEach(() => listResumes.mockReset());

test('preselects the primary resume', async () => {
  listResumes.mockResolvedValue([
    { id: 'r1', fileName: 'old.pdf', primary: false },
    { id: 'r2', fileName: 'main.pdf', primary: true },
  ]);
  render(<ApplyForm jobId="j" onSuccess={() => undefined} />);
  const select = (await screen.findByLabelText('Resume')) as HTMLSelectElement;
  await waitFor(() => expect(select.value).toBe('r2'));
  expect(screen.queryByText(/You need a resume/)).not.toBeInTheDocument();
});

test('links to the profile and disables submit when there is no resume', async () => {
  listResumes.mockResolvedValue([]);
  render(<ApplyForm jobId="j" onSuccess={() => undefined} />);
  const link = await screen.findByRole('link', { name: /Upload one on your profile/ });
  expect(link).toHaveAttribute('href', '/profile');
  expect(screen.getByRole('button', { name: 'Submit application' })).toBeDisabled();
});

test('shows an error when resumes cannot be loaded and no profile hint', async () => {
  listResumes.mockRejectedValue(new Error('boom'));
  render(<ApplyForm jobId="j" onSuccess={() => undefined} />);
  expect(await screen.findByRole('alert')).toHaveTextContent('Could not load your resumes.');
  expect(screen.queryByText(/You need a resume/)).not.toBeInTheDocument();
});
