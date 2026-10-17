import { expect, test } from '@playwright/test';
test('protects the application and exposes registration', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveURL(/\/login$/);
  await page.getByRole('link', { name: 'Create an account' }).click();
  await expect(page.getByRole('heading', { name: 'Create your Khayaal account' })).toBeVisible();
  await expect(page.getByText(/It is not medical advice/)).toBeVisible();
});
