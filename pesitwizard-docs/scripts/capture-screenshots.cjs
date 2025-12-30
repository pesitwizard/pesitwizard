const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const CLIENT_URL = process.env.CLIENT_URL || 'http://localhost:3002';
const ADMIN_URL = process.env.ADMIN_URL || 'http://localhost:3000';
const SCREENSHOT_DIR = path.join(__dirname, '../public/screenshots');

// Screenshot options for consistent quality
const SCREENSHOT_OPTIONS = {
    fullPage: false,
    animations: 'disabled',
    scale: 'css'
};

async function ensureDir(dir) {
    if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
    }
}

async function captureClientScreenshots(page) {
    const clientDir = path.join(SCREENSHOT_DIR, 'client');
    await ensureDir(clientDir);

    console.log('Capturing client screenshots...');

    // Dashboard
    await page.goto(`${CLIENT_URL}/`);
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(clientDir, 'dashboard.png'), fullPage: false });
    console.log('  ✓ dashboard.png');

    // Transfer page - SEND
    await page.goto(`${CLIENT_URL}/transfer`);
    await page.waitForTimeout(500);
    // Select SEND direction using radio button
    await page.click('input[value="SEND"]').catch(() => { });
    await page.waitForTimeout(300);
    await page.screenshot({ path: path.join(clientDir, 'transfer-send.png'), fullPage: false });
    console.log('  ✓ transfer-send.png');

    // Transfer page - RECEIVE with placeholders
    await page.click('input[value="RECEIVE"]').catch(() => { });
    await page.waitForTimeout(500);
    await page.screenshot({ path: path.join(clientDir, 'transfer-receive.png'), fullPage: false });
    console.log('  ✓ transfer-receive.png');

    // Focus on placeholder input to show tags
    const placeholderInput = page.locator('[class*="placeholder"]').first();
    if (await placeholderInput.count() > 0) {
        await placeholderInput.click();
        await page.waitForTimeout(300);
    }
    await page.screenshot({ path: path.join(clientDir, 'path-placeholders.png'), fullPage: false });
    console.log('  ✓ path-placeholders.png');

    // Favorites page
    await page.goto(`${CLIENT_URL}/favorites`);
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(clientDir, 'favorites.png'), fullPage: false });
    console.log('  ✓ favorites.png');

    // Try to click edit on first favorite if exists
    const editBtn = page.locator('button[title="Edit"]').first();
    if (await editBtn.count() > 0) {
        await editBtn.click();
        await page.waitForTimeout(500);
        await page.screenshot({ path: path.join(clientDir, 'favorite-edit.png'), fullPage: false });
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
        await page.screenshot({ path: path.join(clientDir, 'schedule-create.png'), fullPage: false });
        console.log('  ✓ schedule-create.png');
        await page.keyboard.press('Escape');
    }

    // Schedules page
    await page.goto(`${CLIENT_URL}/schedules`);
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(clientDir, 'schedules.png'), fullPage: false });
    console.log('  ✓ schedules.png');

    // Calendars page
    await page.goto(`${CLIENT_URL}/calendars`);
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(clientDir, 'calendars.png'), fullPage: false });
    console.log('  ✓ calendars.png');

    // Try to open calendar creation modal
    const newCalendarBtn = page.locator('button:has-text("New Calendar")').first();
    if (await newCalendarBtn.count() > 0) {
        await newCalendarBtn.click();
        await page.waitForTimeout(500);
        await page.screenshot({ path: path.join(clientDir, 'calendar-form.png'), fullPage: false });
        console.log('  ✓ calendar-form.png');
        await page.keyboard.press('Escape');
        await page.waitForTimeout(300);
    }

    // History page
    await page.goto(`${CLIENT_URL}/history`);
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(clientDir, 'history.png'), fullPage: false });
    console.log('  ✓ history.png');
}

async function captureAdminScreenshots(page) {
    const adminDir = path.join(SCREENSHOT_DIR, 'admin');
    await ensureDir(adminDir);

    console.log('Capturing admin screenshots...');

    // First, login
    await page.goto(`${ADMIN_URL}/login`);
    await page.waitForTimeout(500);

    // Fill login form
    await page.fill('input[type="text"], input[name="username"], input[placeholder*="user" i]', 'admin').catch(() => { });
    await page.fill('input[type="password"]', 'admin').catch(() => { });
    await page.screenshot({ path: path.join(adminDir, 'login.png'), fullPage: false });
    console.log('  ✓ login.png');

    // Submit login
    await page.click('button[type="submit"]').catch(() => { });
    await page.waitForTimeout(1000);

    // Dashboard
    await page.goto(`${ADMIN_URL}/`);
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(adminDir, 'dashboard.png'), fullPage: false });
    console.log('  ✓ dashboard.png');

    // Clusters page
    await page.goto(`${ADMIN_URL}/clusters`);
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(adminDir, 'clusters.png'), fullPage: false });
    console.log('  ✓ clusters.png');

    // Find first cluster ID (exclude /clusters/new)
    let clusterId = null;
    const clusterLinks = page.locator('a[href*="/clusters/"]');
    const count = await clusterLinks.count();
    for (let i = 0; i < count; i++) {
        const href = await clusterLinks.nth(i).getAttribute('href');
        if (href && !href.includes('/new') && !href.includes('/edit')) {
            const match = href.match(/\/clusters\/([a-f0-9-]+)/i);
            if (match) {
                clusterId = match[1];
                break;
            }
        }
    }

    if (clusterId) {
        console.log(`  → Using cluster ID: ${clusterId}`);

        // Cluster detail page - scroll down to show pods
        await page.goto(`${ADMIN_URL}/clusters/${clusterId}`);
        await page.waitForTimeout(1500);
        // Scroll down to show pods section
        await page.evaluate(() => window.scrollBy(0, 300));
        await page.waitForTimeout(500);
        await page.screenshot({ path: path.join(adminDir, 'cluster-detail.png'), fullPage: false });
        console.log('  ✓ cluster-detail.png');

        // Also capture full page for pods visibility
        await page.evaluate(() => window.scrollTo(0, 0));
        await page.waitForTimeout(300);
        await page.screenshot({ path: path.join(adminDir, 'cluster-overview.png'), fullPage: true });
        console.log('  ✓ cluster-overview.png (full page)');

        // Partners page (under cluster)
        await page.goto(`${ADMIN_URL}/clusters/${clusterId}/partners`);
        await page.waitForTimeout(2000);
        await page.waitForSelector('table, .card, [class*="partner"]', { timeout: 5000 }).catch(() => { });
        await page.waitForTimeout(500);
        await page.screenshot({ path: path.join(adminDir, 'partners.png'), fullPage: false });
        console.log('  ✓ partners.png');

        // Try to open partner creation modal
        const addPartnerBtn = page.locator('button:has-text("Add"), button:has-text("Ajouter"), button:has-text("New")').first();
        if (await addPartnerBtn.count() > 0) {
            await addPartnerBtn.click();
            await page.waitForTimeout(800);
            await page.screenshot({ path: path.join(adminDir, 'partner-form.png'), fullPage: false });
            console.log('  ✓ partner-form.png');
            await page.keyboard.press('Escape');
            await page.waitForTimeout(300);
        }

        // Virtual Files page (under cluster)
        await page.goto(`${ADMIN_URL}/clusters/${clusterId}/files`);
        await page.waitForTimeout(2000);
        await page.waitForSelector('table, .card, [class*="file"]', { timeout: 5000 }).catch(() => { });
        await page.waitForTimeout(500);
        await page.screenshot({ path: path.join(adminDir, 'virtual-files.png'), fullPage: false });
        console.log('  ✓ virtual-files.png');

        // Try to open virtual file creation modal
        const addFileBtn = page.locator('button:has-text("Add"), button:has-text("Ajouter"), button:has-text("New")').first();
        if (await addFileBtn.count() > 0) {
            await addFileBtn.click();
            await page.waitForTimeout(800);
            await page.screenshot({ path: path.join(adminDir, 'virtual-file-form.png'), fullPage: false });
            console.log('  ✓ virtual-file-form.png');
            await page.keyboard.press('Escape');
            await page.waitForTimeout(300);
        }

        // Transfers page (under cluster)
        await page.goto(`${ADMIN_URL}/clusters/${clusterId}/transfers`);
        await page.waitForTimeout(1000);
        await page.screenshot({ path: path.join(adminDir, 'transfers.png'), fullPage: false });
        console.log('  ✓ transfers.png');

        // Audit logs page
        await page.goto(`${ADMIN_URL}/clusters/${clusterId}/audit`);
        await page.waitForTimeout(1000);
        await page.screenshot({ path: path.join(adminDir, 'audit.png'), fullPage: false });
        console.log('  ✓ audit.png');

        // Certificates page
        await page.goto(`${ADMIN_URL}/clusters/${clusterId}/certificates`);
        await page.waitForTimeout(1000);
        await page.screenshot({ path: path.join(adminDir, 'certificates.png'), fullPage: false });
        console.log('  ✓ certificates.png');

        // Try to open certificate generation modal
        const newCertBtn = page.locator('button:has-text("Generate"), button:has-text("Générer"), button:has-text("New")').first();
        if (await newCertBtn.count() > 0) {
            await newCertBtn.click();
            await page.waitForTimeout(800);
            await page.screenshot({ path: path.join(adminDir, 'certificate-form.png'), fullPage: false });
            console.log('  ✓ certificate-form.png');
            await page.keyboard.press('Escape');
            await page.waitForTimeout(300);
        }

        // Settings/Configuration page if exists
        await page.goto(`${ADMIN_URL}/clusters/${clusterId}/settings`);
        await page.waitForTimeout(1000);
        const settingsContent = page.locator('main, .content, [class*="settings"]');
        if (await settingsContent.count() > 0) {
            await page.screenshot({ path: path.join(adminDir, 'cluster-settings.png'), fullPage: false });
            console.log('  ✓ cluster-settings.png');
        }
    } else {
        console.log('  ⚠ No cluster found, skipping cluster-specific pages');
    }

    // Registries page
    await page.goto(`${ADMIN_URL}/registries`);
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(adminDir, 'registries.png'), fullPage: false });
    console.log('  ✓ registries.png');

    // Try to open registry creation modal
    const addRegistryBtn = page.locator('button:has-text("Add"), button:has-text("Ajouter"), button:has-text("New")').first();
    if (await addRegistryBtn.count() > 0) {
        await addRegistryBtn.click();
        await page.waitForTimeout(800);
        await page.screenshot({ path: path.join(adminDir, 'registry-form.png'), fullPage: false });
        console.log('  ✓ registry-form.png');
        await page.keyboard.press('Escape');
        await page.waitForTimeout(300);
    }

    // Orchestrators page
    await page.goto(`${ADMIN_URL}/orchestrators`);
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(adminDir, 'orchestrators.png'), fullPage: false });
    console.log('  ✓ orchestrators.png');

    // Try to open orchestrator creation modal
    const addOrchBtn = page.locator('button:has-text("Add"), button:has-text("Ajouter"), button:has-text("New")').first();
    if (await addOrchBtn.count() > 0) {
        await addOrchBtn.click();
        await page.waitForTimeout(800);
        await page.screenshot({ path: path.join(adminDir, 'orchestrator-form.png'), fullPage: false });
        console.log('  ✓ orchestrator-form.png');
        await page.keyboard.press('Escape');
        await page.waitForTimeout(300);
    }

    // Cluster creation page
    await page.goto(`${ADMIN_URL}/clusters/new`);
    await page.waitForTimeout(1000);
    await page.screenshot({ path: path.join(adminDir, 'cluster-form.png'), fullPage: false });
    console.log('  ✓ cluster-form.png');
}

async function main() {
    console.log('Starting screenshot capture...\n');

    const browser = await chromium.launch({
        headless: true,
    });

    const context = await browser.newContext({
        viewport: { width: 1280, height: 800 },
        deviceScaleFactor: 2, // Retina quality
    });

    const page = await context.newPage();

    try {
        // Capture client screenshots
        await captureClientScreenshots(page);
        console.log('');

        // Try admin screenshots (may fail if not running)
        try {
            await captureAdminScreenshots(page);
        } catch (e) {
            console.log('Admin UI not available, skipping admin screenshots');
        }

        console.log('\n✅ Screenshots captured successfully!');
        console.log(`   Location: ${SCREENSHOT_DIR}`);
    } catch (error) {
        console.error('Error capturing screenshots:', error.message);
        process.exit(1);
    } finally {
        await browser.close();
    }
}

main();
