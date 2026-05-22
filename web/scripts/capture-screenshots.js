const fs = require("node:fs/promises");
const path = require("node:path");
const { chromium } = require("playwright");

const baseUrl = process.env.WEB_BASE_URL || "http://localhost:3000";
const outputDir = path.resolve(__dirname, "..", "..", "docs", "images");

const credentials = {
  email: "superadmin@hadivo.local",
  password: "ChangeMe123!",
};

const desktopViewport = { width: 1440, height: 900 };

async function ensureOutputDir() {
  await fs.mkdir(outputDir, { recursive: true });
}

async function waitForDashboardData(page) {
  await page.waitForLoadState("networkidle");
  await page.waitForFunction(
    () => !document.body.innerText.includes("Preparing dashboard") && !document.body.innerText.includes("Loading "),
    null,
    { timeout: 30000 },
  );
  await page.waitForTimeout(750);
}

async function prepareScreenshot(page) {
  await page.mouse.move(1, 1);
  await page.addStyleTag({
    content: "nextjs-portal, #nextjs__container { display: none !important; }",
  });
}

async function assertNoHorizontalOverflow(page, label) {
  const overflow = await page.evaluate(() => {
    const root = document.documentElement;
    const body = document.body;
    return Math.max(root.scrollWidth, body.scrollWidth) - root.clientWidth;
  });

  if (overflow > 2) {
    throw new Error(`${label} has horizontal overflow of ${overflow}px`);
  }
}

async function login(page) {
  await page.goto(`${baseUrl}/login`, { waitUntil: "networkidle" });
  await prepareScreenshot(page);
  await page.screenshot({ path: path.join(outputDir, "web-login.png"), fullPage: true });
  await page.fill("#email", credentials.email);
  await page.fill("#password", credentials.password);
  await page.getByRole("button", { name: /Masuk|Sign in/i }).click();
  await page.waitForURL("**/dashboard", { timeout: 30000 });
  await waitForDashboardData(page);
}

async function capturePage(page, route, filename) {
  await page.goto(`${baseUrl}${route}`, { waitUntil: "networkidle" });
  await waitForDashboardData(page);
  await prepareScreenshot(page);
  await page.screenshot({ path: path.join(outputDir, filename), fullPage: true });
}

async function captureResponsive(page) {
  const viewports = [
    { name: "mobile", width: 390, height: 844, file: "responsive-dashboard-mobile.png" },
    { name: "tablet", width: 768, height: 1024, file: "responsive-dashboard-tablet.png" },
    { name: "desktop", width: 1440, height: 900, file: "responsive-dashboard-desktop.png" },
  ];

  for (const viewport of viewports) {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await page.goto(`${baseUrl}/dashboard`, { waitUntil: "networkidle" });
    await waitForDashboardData(page);
    await assertNoHorizontalOverflow(page, `Dashboard ${viewport.name}`);
    await prepareScreenshot(page);
    await page.screenshot({ path: path.join(outputDir, viewport.file), fullPage: true });
  }
}

async function main() {
  await ensureOutputDir();

  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport: desktopViewport });
  const page = await context.newPage();

  try {
    await login(page);
    await prepareScreenshot(page);
    await page.screenshot({ path: path.join(outputDir, "web-dashboard.png"), fullPage: true });

    await capturePage(page, "/attendance", "web-attendance.png");
    await capturePage(page, "/attendance-attempts", "web-attempts.png");
    await capturePage(page, "/settings", "web-settings.png");
    await capturePage(page, "/locations", "web-locations.png");
    await capturePage(page, "/subscription", "web-subscription.png");
    await captureResponsive(page);
  } finally {
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
