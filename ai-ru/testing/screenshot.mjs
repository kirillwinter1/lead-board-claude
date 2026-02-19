import { chromium } from 'playwright';

const SESSION_COOKIE = process.argv[2];
const URL = process.argv[3] || 'http://localhost:5173/board';
const OUTPUT = process.argv[4] || 'screenshot.png';
const FULL_PAGE = process.argv.includes('--full');

if (!SESSION_COOKIE) {
  console.error('Usage: node screenshot.mjs <session-cookie> [url] [output] [--full]');
  process.exit(1);
}

const browser = await chromium.launch();
const context = await browser.newContext({
  viewport: { width: 1920, height: 1080 },
});

await context.addCookies([{
  name: 'LEAD_SESSION',
  value: SESSION_COOKIE,
  domain: 'localhost',
  path: '/',
}]);

const page = await context.newPage();
await page.goto(URL, { waitUntil: 'networkidle', timeout: 15000 });
await page.waitForTimeout(2000);

await page.screenshot({ path: OUTPUT, fullPage: FULL_PAGE });
console.log(`Screenshot saved: ${OUTPUT}`);

await browser.close();
