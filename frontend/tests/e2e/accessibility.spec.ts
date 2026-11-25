import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

test('public job discovery meets WCAG 2.1 A/AA checks', async ({ page }) => {
  await page.goto('/jobs');
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  expect(results.violations, JSON.stringify(results.violations, null, 2)).toEqual([]);
});
