import { expect, test } from '@playwright/test';
import { mkdirSync } from 'node:fs';

test('registers and creates a household, person, and medicine schedule', async ({
  page
}, testInfo) => {
  const capture = async (name: string) => {
    if (testInfo.project.name !== 'chromium') return;
    mkdirSync('../docs/screenshots', { recursive: true });
    await page.screenshot({ path: `../docs/screenshots/${name}.png`, fullPage: true });
  };
  const email = `phase5-${Date.now()}@example.test`;
  await page.goto('/register');
  await page.getByLabel('Your name').fill('Synthetic Caregiver');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill('correct-horse-battery');
  const registrationResponse = page.waitForResponse(
    (response) =>
      response.url().endsWith('/api/auth/register') && response.request().method() === 'POST',
    { timeout: 30_000 }
  );
  await page.getByRole('button', { name: 'Create account' }).click();
  expect((await registrationResponse).ok()).toBeTruthy();
  await expect(page.getByRole('heading', { name: 'Today' })).toBeVisible();
  await page
    .getByRole('link', { name: /Family/ })
    .first()
    .click();
  await page.getByRole('button', { name: 'Create my household' }).click();
  await expect(page.getByRole('heading', { name: 'Family' })).toBeVisible();
  await page.getByRole('button', { name: 'Add person' }).click();
  await page.getByLabel('Name').fill('Synthetic Parent');
  await page.getByRole('button', { name: 'Save person' }).click();
  await page.getByRole('link', { name: /Synthetic Parent/ }).click();
  await page.getByRole('link', { name: 'Add medicine' }).click();
  await page.getByLabel('Medicine name').fill('Synthetic medicine');
  await page.getByLabel('Strength (as written)').fill('user-entered');
  await page.getByLabel('Add the first purchase now').check();
  await page.getByLabel('Initial packs').fill('1');
  await page.getByLabel('Initial units per pack').fill('3');
  const shortlyFromNow = await page.evaluate(() => {
    const date = new Date(Date.now() + 5 * 60_000);
    return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
  });
  await page.getByLabel('Dose time 1').fill(shortlyFromNow);
  await capture('schedule-builder');
  await page.getByRole('button', { name: 'Add medicine' }).click();
  await expect(page.getByText('Synthetic medicine')).toBeVisible();
  await page.getByRole('link', { name: /Today/ }).first().click();
  const doseCard = page.getByRole('article', { name: /Synthetic medicine/ });
  await expect(doseCard).toBeVisible();
  await doseCard.getByRole('button', { name: 'Taken' }).click();
  await expect(doseCard.getByText('TAKEN')).toBeVisible();
  await expect(doseCard.getByRole('button', { name: 'Undo' })).toBeVisible();
  await capture('today');
  await page.getByRole('link', { name: /Stock/ }).first().click();
  const stockCard = page.getByRole('article').filter({ hasText: 'Synthetic medicine' });
  await expect(stockCard.getByText('2 units remaining')).toBeVisible();
  await stockCard.getByRole('button', { name: 'Add purchase' }).click();
  await page.getByLabel('Packs').fill('1');
  await page.getByLabel('Units per pack').fill('2');
  await page.getByRole('button', { name: 'Save purchase' }).click();
  await expect(stockCard.getByText('4 units remaining')).toBeVisible();
  await stockCard.getByRole('button', { name: 'Mark ordered' }).click();
  await expect(stockCard.getByRole('button', { name: 'Ordered' })).toBeDisabled();
  await capture('stock');
});
