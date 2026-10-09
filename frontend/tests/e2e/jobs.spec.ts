import { test, expect } from '@playwright/test';

test('public jobs page is reachable', async ({ page }) => {
  await page.goto('/jobs');
  await expect(page.getByRole('heading', { name: 'Find jobs' })).toBeVisible();
});
