const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

// OSS Client screenshots only - Admin screenshots are in pesitwizard-enterprise/docs
const CLIENT_URL = process.env.CLIENT_URL || 'http://localhost:3002';
const SCREENSHOT_DIR = path.join(__dirname, '../public/screenshots/client');

async function ensureDir(dir) {
    if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
    }
}

async function captureClientScreenshots(page) {
    await ensureDir(SCREENSHOT_DIR);

    console.log('Capturing client screenshots...');

    // Dashboard
    await page.goto(`${CLIENT_URL}/`);
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'dashboard.png'), fullPage: false });
    console.log('  ✓ dashboard.png');

    // Transfer page - SEND
    await page.goto(`${CLIENT_URL}/transfer`);
    await page.waitForTimeout(500);
    // Select SEND direction using radio button
    await page.click('input[value="SEND"]').catch(() => { });
    await page.waitForTimeout(300);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'transfer-send.png'), fullPage: false });
    console.log('  ✓ transfer-send.png');

    // Transfer page - RECEIVE with placeholders
    await page.click('input[value="RECEIVE"]').catch(() => { });
    await page.waitForTimeout(500);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'transfer-receive.png'), fullPage: false });
    console.log('  ✓ transfer-receive.png');

    // Focus on placeholder input to show tags
    const placeholderInput = page.locator('[class*="placeholder"]').first();
    if (await placeholderInput.count() > 0) {
        await placeholderInput.click();
        await page.waitForTimeout(300);
    }
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'path-placeholders.png'), fullPage: false });
    console.log('  ✓ path-placeholders.png');

    // Favorites page
    await page.goto(`${CLIENT_URL}/favorites`);
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'favorites.png'), fullPage: false });
    console.log('  ✓ favorites.png');

    // Try to click edit on first favorite if exists
    const editBtn = page.locator('button[title="Edit"]').first();
    if (await editBtn.count() > 0) {
        await editBtn.click();
        await page.waitForTimeout(500);
        await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'favorite-edit.png'), fullPage: false });
        console.log('  ✓ favorite-edit.png');
        // Close modal
        await page.keyboard.press('Escape');
    }

    // Try to open schedule modal from favorites
    await page.goto(`${CLIENT_URL}/favorites`);
    await page.waitForTimeout(500);
    const scheduleBtn = page.locator('button[title="Schedule"]').first();
    if (await scheduleBtn.count() > 0) {
        await scheduleBtn.click();
        await page.waitForTimeout(500);
        await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'schedule-create.png'), fullPage: false });
        console.log('  ✓ schedule-create.png');
        await page.keyboard.press('Escape');
    }

    // Schedules page
    await page.goto(`${CLIENT_URL}/schedules`);
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'schedules.png'), fullPage: false });
    console.log('  ✓ schedules.png');

    // Calendars page
    await page.goto(`${CLIENT_URL}/calendars`);
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'calendars.png'), fullPage: false });
    console.log('  ✓ calendars.png');

    // Try to open calendar creation modal
    const newCalendarBtn = page.locator('button:has-text("New Calendar")').first();
    if (await newCalendarBtn.count() > 0) {
        await newCalendarBtn.click();
        await page.waitForTimeout(500);
        await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'calendar-form.png'), fullPage: false });
        console.log('  ✓ calendar-form.png');
        await page.keyboard.press('Escape');
        await page.waitForTimeout(300);
    }

    // History page
    await page.goto(`${CLIENT_URL}/history`);
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'history.png'), fullPage: false });
    console.log('  ✓ history.png');
}

async function main() {
    console.log('Starting PeSIT Wizard Client screenshot capture...\n');
    console.log(`Client URL: ${CLIENT_URL}`);
    console.log(`Output: ${SCREENSHOT_DIR}\n`);

    const browser = await chromium.launch({
        headless: true,
    });

    const context = await browser.newContext({
        viewport: { width: 1280, height: 800 },
        deviceScaleFactor: 2, // Retina quality
    });

    const page = await context.newPage();

    try {
        await captureClientScreenshots(page);
        console.log('\n✅ Client screenshots captured successfully!');
        console.log(`   Location: ${SCREENSHOT_DIR}`);
    } catch (error) {
        console.error('Error capturing screenshots:', error.message);
        process.exit(1);
    } finally {
        await browser.close();
    }
}

main();
