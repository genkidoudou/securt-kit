const path = require("path");
const { chromium } = require("./node_modules/playwright");

const base = (process.argv[2] || "http://127.0.0.1:18080").replace(/\/$/, "");
const url = base + "/playground/";
const chromiumPath = path.join(
  process.env.USERPROFILE || "",
  "AppData/Local/ms-playwright/chromium-1217/chrome-win64/chrome.exe"
);

async function main() {
  const browser = await chromium.launch({
    headless: true,
    executablePath: chromiumPath
  });
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  const errors = [];
  page.on("pageerror", (err) => errors.push(String(err)));
  page.on("console", (msg) => {
    if (msg.type() !== "error") return;
    const text = msg.text();
    if (text.indexOf("Failed to load resource") >= 0 && text.indexOf("404") >= 0) return;
    errors.push(text);
  });

  await page.goto(url, { waitUntil: "networkidle" });
  await page.waitForSelector("#lifecycleSteps");
  await page.waitForFunction(() => {
    const el = document.getElementById("preflightSummary");
    return el && el.textContent && el.textContent.length > 0;
  });

  async function submitActive(expectedStep) {
    let form = page.locator('.step-form:not([hidden])');
    await form.waitFor();
    const formStep = await form.getAttribute("data-form");
    if (formStep !== expectedStep) {
      await page.click('[data-step="' + expectedStep + '"]');
      form = page.locator('.step-form:not([hidden])');
      await form.waitFor();
    }
    if (expectedStep === "insert") {
      await page.fill("#insertName", "浏览器烟测");
      await page.fill("#insertPhone", "13800138000");
      await page.fill("#insertEmail", "browser@example.com");
    }
    if (expectedStep === "update") {
      await page.fill("#updatePhone", "13900139000");
    }
    if (expectedStep === "tamper") {
      page.once("dialog", async (dialog) => dialog.accept());
    }
    await page.locator('.step-form:not([hidden]) button[type="submit"]').click();
    await page.waitForFunction((step) => {
      const button = document.querySelector('[data-step="' + step + '"]');
      return button && button.classList.contains("passed");
    }, expectedStep, { timeout: 20000 });
    const raw = await page.textContent("#rawJson");
    const business = await page.textContent("#businessJson");
    if (!raw || raw.trim() === "{}") throw new Error(expectedStep + " missing raw evidence");
    if (expectedStep === "tamper") {
      const error = await page.textContent("#errorJson");
      if (!error || error.indexOf("DIGEST_VERIFICATION") < 0) {
        throw new Error("tamper should expose DIGEST_VERIFICATION error");
      }
    } else if (!business || business.trim() === "{}") {
      throw new Error(expectedStep + " missing business evidence");
    }
  }

  for (const step of ["insert", "query", "update", "queryAgain", "verify", "tamper"]) {
    await submitActive(step);
  }

  const more = page.locator("#moreTests");
  await more.locator("summary").click();
  await page.waitForSelector("#complexActions");
  await page.waitForSelector("#multiCompareBtn");
  await page.waitForSelector("#sqlMonitorLink");

  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(300);
  const columns = await page.evaluate(() => {
    const grid = document.querySelector(".evidence-grid");
    return grid ? getComputedStyle(grid).gridTemplateColumns : "";
  });
  if (!columns) throw new Error("narrow evidence layout missing");

  await browser.close();
  if (errors.length) {
    throw new Error("console errors: " + errors.join(" | "));
  }
  console.log("BROWSER SMOKE OK", url, "narrowColumns=" + columns);
}

main().catch((err) => {
  console.error("BROWSER SMOKE FAIL", url, err.message);
  process.exit(1);
});
