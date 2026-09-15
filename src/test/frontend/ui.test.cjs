const { JSDOM } = require('jsdom');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { test } = require('node:test');
const ROOT = path.resolve(__dirname, '../../main/resources/static');
const tick = () => new Promise(resolve => setImmediate(resolve));

async function setup(persona = 1) {
    const facilities = [
        { id: 1, name: 'Central Medical Warehouse', type: 'WAREHOUSE' },
        { id: 2, name: 'Unity Hospital', type: 'HOSPITAL' },
        { id: 3, name: 'City Hospital', type: 'HOSPITAL' }
    ];
    const inventories = facilities.map(f => ({ id: f.id, facilityId: f.id, facilityName: f.name, medicineId: 1,
        medicineName: 'Test medicine', genericName: 'Test', unit: 'units', quantity: f.id === 1 ? 5000 : 300,
        dailyConsumption: 10, reorderLevel: 100, expiryDate: '2028-12-31', daysRemaining: 30,
        safeTransferQuantity: f.id === 1 ? 4900 : 150, risk: 'HEALTHY', recommendation: 'Stock is healthy',
        safetyFloor: 100, hospitalBuffer: f.type === 'HOSPITAL' ? 50 : 0, reservedQuantity: 0 }));
    const request = { id: 1, requesterId: 2, requesterName: 'Unity Hospital', medicineId: 1, medicineName: 'Test medicine',
        quantity: 200, priority: 'CRITICAL', status: 'REQUESTED', createdAt: '2026-09-15T09:00:00',
        approvedQuantity: 0, dispatchedQuantity: 0, deliveredQuantity: 0, remainingQuantity: 200,
        allocations: [{ id: 1, sourceId: 1, sourceName: facilities[0].name, quantity: 200, state: 'PENDING', updatedAt: '2026-09-15T09:00:00' }] };
    const html = fs.readFileSync(path.join(ROOT, 'index.html'), 'utf8').replace(/<script[^>]*src="app.js[^>]*><\/script>/, '');
    const dom = new JSDOM(html, { url: 'http://medihive.test/', runScripts: 'outside-only', pretendToBeVisual: true });
    const w = dom.window, doc = w.document;
    // JSDOM has no native dialog rendering. Simulate its open/close semantics for interaction tests.
    w.HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', ''); };
    w.HTMLDialogElement.prototype.close = function () { if (this.open) { this.removeAttribute('open'); this.dispatchEvent(new w.Event('close')); } };
    w.confirm = w.prompt = w.alert = () => { throw Error('A native browser pop-up was used'); };
    const calls = []; const controls = { failNext: false, delayNext: null };
    w.fetch = async (url, options = {}) => {
        const u = new URL(url, 'http://medihive.test');
        const method = options.method || 'GET'; const body = options.body ? JSON.parse(options.body) : undefined;
        calls.push({ url: u.pathname + u.search, method, body });
        const result = value => ({ ok: true, status: 200, redirected: false, json: async () => structuredClone(value) });
        if (method !== 'GET') {
            if (controls.delayNext) { const wait = controls.delayNext; controls.delayNext = null; await wait; }
            if (controls.failNext) { controls.failNext = false; return { ok: false, status: 400, json: async () => ({ message: 'Stock changed. Maximum safe offer is now 20 units.' }) }; }
            if (method === 'PUT') { Object.assign(inventories[body.facilityId || Number(u.pathname.split('/').pop()) - 1], body); return result({}); }
            if (u.pathname.endsWith('/offers')) {
                let a = request.allocations.find(a => a.sourceId === body.sourceId && a.state === 'PENDING');
                if (!a) { a = { id: 2, sourceId: body.sourceId, sourceName: facilities[body.sourceId - 1].name, state: 'PENDING' }; request.allocations.push(a); }
                a.quantity = body.quantity;
            }
            return result(request);
        }
        if (u.pathname === '/api/me') return result({ role: facilities[persona - 1].type, facilityName: facilities[persona - 1].name });
        if (u.pathname === '/api/facilities') return result(facilities);
        if (u.pathname === '/api/medicines') return result([{ id: 1, name: 'Test medicine' }]);
        if (u.pathname.startsWith('/api/dashboard/')) {
            const i = Number(u.pathname.split('/').pop()) - 1;
            return result({ facilityName: facilities[i].name, inventory: [inventories[i]], requests: [request], medicinesTracked: 1,
                criticalAlerts: 0, expiringSoon: 0, openRequests: 1 });
        }
        if (u.pathname === '/api/inventory') return result([inventories[Number(u.searchParams.get('facilityId')) - 1]]);
        if (u.pathname.endsWith('/offer-options')) {
            const sourceId = Number(u.searchParams.get('sourceId')), s = inventories[sourceId - 1];
            return result({ sourceId, sourceName: s.facilityName, currentQuantity: s.quantity, safetyFloor: 100,
                hospitalBuffer: s.hospitalBuffer, reservedQuantity: 0, safeTransferQuantity: s.safeTransferQuantity,
                unapprovedQuantity: 200, maxOfferQuantity: Math.min(200, s.safeTransferQuantity), proposedQuantity: 0 });
        }
        if (u.pathname === '/api/suggestions') return result([]);
        throw Error('Unexpected endpoint: ' + url);
    };
    await new Promise(resolve => doc.addEventListener('DOMContentLoaded', resolve, { once: true }));
    w.eval(fs.readFileSync(path.join(ROOT, 'app.js'), 'utf8'));
    doc.dispatchEvent(new w.Event('DOMContentLoaded'));
    await tick();
    const $ = id => doc.getElementById(id);
    const submit = id => $(id).dispatchEvent(new w.Event('submit', { bubbles: true, cancelable: true }));
    return { dom, w, doc, $, submit, calls, controls };
}

test('Warehouse edit controls disappear for every other facility', async () => {
    const s = await setup();
    try {
        assert.equal(s.doc.querySelectorAll('[data-edit]').length, 1);
        for (const id of ['2', '3']) {
            s.$('facilitySelect').value = id; s.$('facilitySelect').dispatchEvent(new s.w.Event('change')); await tick();
            assert.equal(s.doc.querySelectorAll('[data-edit]').length, 0);
            assert.equal(s.$('inventoryManageHeader').hidden, true);
            assert.match(s.$('inventoryAccessNote').textContent, /View only/);
        }
    } finally { s.dom.window.close(); }
});

test('Edit requires the built-in confirmation and Cancel makes no changes', async () => {
    const s = await setup();
    try {
        s.doc.querySelector('[data-edit]').click(); s.$('editQuantity').value = '4800'; s.submit('editForm');
        assert.equal(s.$('actionDialog').open, true);
        assert.match(s.$('actionDetails').textContent, /5000 to 4800/);
        s.$('cancelAction').click(); assert.equal(s.calls.filter(c => c.method === 'PUT').length, 0);
        s.submit('editForm'); s.submit('actionForm'); await tick();
        assert.equal(s.calls.filter(c => c.method === 'PUT').length, 1);
        assert.equal(s.calls.find(c => c.method === 'PUT').body.quantity, 4800);
        assert.equal(s.$('editDialog').open, false);
    } finally { s.dom.window.close(); }
});

test('Critical hospital offer shows its 50-unit buffer and rejects excessive quantities', async () => {
    const s = await setup(3);
    try {
        assert.equal(s.doc.querySelectorAll('[data-edit]').length, 0);
        s.doc.querySelector('[data-action="offer"]').click(); await tick();
        assert.equal(s.$('actionDialog').open, true);
        assert.equal(s.$('actionQuantity').max, '150');
        assert.match(s.$('actionDetails').textContent, /Extra hospital reserve50/);
        assert.match(s.$('actionHint').textContent, /150 units remain/);
        s.$('actionQuantity').value = '151'; s.submit('actionForm'); await tick();
        assert.equal(s.calls.filter(c => c.method === 'POST').length, 0);
        assert.equal(s.$('actionError').hidden, false);
        s.$('actionQuantity').value = '150'; s.submit('actionForm'); await tick();
        assert.deepEqual(s.calls.find(c => c.method === 'POST').body, { sourceId: 3, quantity: 150 });
        assert.equal(s.$('actionDialog').open, false);
    } finally { s.dom.window.close(); }
});

test('Warehouse offers its own stock even while viewing a hospital', async () => {
    const s = await setup();
    try {
        s.$('facilitySelect').value = '2'; s.$('facilitySelect').dispatchEvent(new s.w.Event('change')); await tick();
        s.doc.querySelector('[data-action="offer"]').click(); await tick();
        assert.ok(s.calls.some(c => c.url === '/api/requests/1/offer-options?sourceId=1'));
        assert.match(s.$('actionDetails').textContent, /Central Medical Warehouse/);
        s.$('actionQuantity').value = '50'; s.submit('actionForm'); await tick();
        assert.equal(s.calls.find(c => c.method === 'POST').body.sourceId, 1);
    } finally { s.dom.window.close(); }
});

test('Server errors remain visible in the dialog and double submission sends one request', async () => {
    const s = await setup(3);
    try {
        s.doc.querySelector('[data-action="offer"]').click(); await tick();
        s.controls.failNext = true; s.submit('actionForm'); await tick();
        assert.equal(s.$('actionDialog').open, true);
        assert.match(s.$('actionError').textContent, /Stock changed/);
        let finish;
        s.controls.delayNext = new Promise(resolve => finish = resolve);
        s.submit('actionForm'); s.submit('actionForm');
        assert.equal(s.calls.filter(c => c.method === 'POST').length, 2); // failed attempt plus one retry
        assert.equal(s.$('confirmAction').disabled, true);
        finish(); await tick(); assert.equal(s.$('actionDialog').open, false);
    } finally { s.dom.window.close(); }
});
