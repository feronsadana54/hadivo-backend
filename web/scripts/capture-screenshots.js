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
  // Background react-query refetches can keep the network active indefinitely
  // on data-heavy routes (e.g. /super-admin/tenants), so treat networkidle as
  // a best-effort hint rather than a hard requirement. The text check below
  // is the authoritative signal that initial loading skeletons are gone.
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await page.waitForFunction(
    () =>
      !document.body.innerText.includes("Preparing dashboard") &&
      !document.body.innerText.includes("Loading ") &&
      !document.body.innerText.includes("Memuat "),
    null,
    { timeout: 60000 },
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
  // Dev-mode HMR keeps the network busy, so `networkidle` is unreliable here.
  // Wait for `load` and then for the email field to actually be present.
  await page.goto(`${baseUrl}/login`, { waitUntil: "load", timeout: 60000 });
  await page.waitForSelector("#email", { timeout: 30000 });
  await prepareScreenshot(page);
  await page.screenshot({ path: path.join(outputDir, "web-login.png"), fullPage: true });
  await page.fill("#email", credentials.email);
  await page.fill("#password", credentials.password);
  await page.getByRole("button", { name: /Masuk|Sign in/i }).click();
  await page.waitForURL("**/dashboard", { timeout: 60000 });
  await waitForDashboardData(page);
}

async function capturePage(page, route, filename) {
  console.log(`[capture] ${route} -> ${filename}`);
  await page.goto(`${baseUrl}${route}`, { waitUntil: "load", timeout: 60000 });
  await waitForDashboardData(page);
  await assertNoHorizontalOverflow(page, route);
  await prepareScreenshot(page);
  await page.screenshot({ path: path.join(outputDir, filename), fullPage: true });
}

async function captureSuperAdminTenants(page) {
  console.log("[capture] /super-admin/tenants -> web-super-admin-tenants.png");
  await page.goto(`${baseUrl}/super-admin/tenants`, { waitUntil: "load", timeout: 60000 });
  // Skip generic waitForDashboardData here: this page can stay in a "Memuat …"
  // state across react-query refetches. The page-specific tbody wait below is
  // a more reliable signal that the data we want to screenshot is in the DOM.
  await page.fill("#tenant-search", "Hadivo Demo");
  await page.locator("tbody").getByText("Hadivo Demo School").waitFor({ timeout: 60000 });
  await page.waitForTimeout(750);
  await assertNoHorizontalOverflow(page, "Super Admin tenants");
  await prepareScreenshot(page);
  await page.screenshot({ path: path.join(outputDir, "web-super-admin-tenants.png"), fullPage: true });
}

async function assertDashboardRoutesResponsive(page) {
  const routes = [
    "/notifications",
    "/members",
    "/shifts",
    "/leave-requests",
    "/calendar",
    "/super-admin",
    "/super-admin/tenants",
    "/super-admin/tenants/11111111-1111-1111-1111-111111111111",
  ];
  const viewports = [
    { name: "mobile", width: 390, height: 844 },
    { name: "tablet", width: 768, height: 1024 },
  ];

  // For responsive overflow checks we only need the layout to be settled, not
  // the data fully loaded. Routes like /super-admin/tenants run background
  // react-query refetches that keep network active beyond the 30s default
  // `networkidle` timeout when each route is revisited per viewport.
  for (const viewport of viewports) {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    for (const route of routes) {
      await page.goto(`${baseUrl}${route}`, { waitUntil: "load", timeout: 60000 });
      await page.waitForTimeout(750);
      await assertNoHorizontalOverflow(page, `Dashboard ${route} ${viewport.name}`);
    }
  }
}

async function captureResponsive(page) {
  const viewports = [
    { name: "mobile", width: 390, height: 844, file: "responsive-dashboard-mobile.png" },
    { name: "tablet", width: 768, height: 1024, file: "responsive-dashboard-tablet.png" },
    { name: "desktop", width: 1440, height: 900, file: "responsive-dashboard-desktop.png" },
  ];

  for (const viewport of viewports) {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await page.goto(`${baseUrl}/dashboard`, { waitUntil: "load", timeout: 60000 });
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
    await capturePage(page, "/leave-requests", "web-leave-requests.png");
    await capturePage(page, "/calendar", "web-calendar.png");
    await capturePage(page, "/members", "web-members.png");
    await capturePage(page, "/shifts", "web-shifts.png");
    await capturePage(page, "/settings", "web-settings.png");
    await capturePage(page, "/locations", "web-locations.png");
    await capturePage(page, "/subscription", "web-subscription.png");
    await capturePage(page, "/notifications", "web-notifications.png");
    await capturePage(page, "/super-admin", "web-super-admin.png");
    await captureSuperAdminTenants(page);
    await capturePage(
      page,
      "/super-admin/tenants/11111111-1111-1111-1111-111111111111",
      "web-super-admin-tenant-detail.png",
    );
    await captureResponsive(page);
    await assertDashboardRoutesResponsive(page);
  } finally {
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
