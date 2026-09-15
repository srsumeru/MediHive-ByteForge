const API = '/api';
const state = {
    facilities: [], medicines: [], role: '', facilityName: '', actorFacilityId: null,
    selectedFacilityId: null, loadedFacilityId: null, inventory: [], ownInventory: [],
    requests: [], editing: null, loadVersion: 0, pendingAction: null, actionBusy: false
};
const $ = id => document.getElementById(id);
let toastTimer;
document.addEventListener('DOMContentLoaded', init);

async function init() {
    bind();
    try {
        const me = await get('/me');
        state.role = me.role;
        state.facilityName = me.facilityName;
        $('roleLabel').textContent = me.facilityName + ' Admin';
        $('dispatchFeedSection').hidden = state.role !== 'WAREHOUSE';
        [state.facilities, state.medicines] = await Promise.all([get('/facilities'), get('/medicines')]);
        const actor = state.facilities.find(f => f.name === state.facilityName && f.type === state.role);
        if (!actor) throw Error('Your account has no matching facility. Please contact the administrator.');
        state.actorFacilityId = actor.id;
        const allowed = state.facilities.filter(f => state.role === 'WAREHOUSE' || f.id === actor.id);
        $('facilitySelect').innerHTML = allowed.map(f => `<option value="${f.id}">${html(f.name)}</option>`).join('');
        $('medicineId').innerHTML = state.medicines.map(m => `<option value="${m.id}">${html(m.name)}</option>`).join('');
        state.selectedFacilityId = actor.id;
        $('facilitySelect').value = String(actor.id);
        $('newRequestButton').hidden = state.role === 'WAREHOUSE';
        await load();
    } catch (e) { toast(e.message, true); }
}

function bind() {
    $('facilitySelect').addEventListener('change', () => {
        state.selectedFacilityId = Number($('facilitySelect').value);
        state.editing = null;
        state.inventory = []; state.requests = []; state.loadedFacilityId = null;
        $('editDialog').close(); $('requestDialog').close();
        renderInventory(); renderRequests(); load();
    });
    $('refreshButton').addEventListener('click', load);
    $('newRequestButton').addEventListener('click', () => {
        if (state.role === 'WAREHOUSE' || state.loadedFacilityId !== state.selectedFacilityId) return;
        syncRequester(); $('requestDialog').showModal(); suggest();
    });
    ['closeDialog', 'cancelDialog'].forEach(id => $(id).addEventListener('click', () => $('requestDialog').close()));
    ['closeEdit', 'cancelEdit'].forEach(id => $(id).addEventListener('click', () => $('editDialog').close()));
    $('medicineId').addEventListener('change', suggest);
    $('priority').addEventListener('change', suggest);
    $('requestForm').addEventListener('submit', createRequest);
    $('editForm').addEventListener('submit', saveInventory);
    $('inventoryBody').addEventListener('click', e => {
        const button = e.target.closest('[data-edit]');
        if (button) editInventory(Number(button.dataset.edit));
    });
    $('requestList').addEventListener('click', e => {
        const b = e.target.closest('[data-action]');
        if (b && !b.disabled) action(b.dataset.action, Number(b.dataset.id)).catch(err => toast(err.message, true));
    });
    $('logoutButton').addEventListener('click', logout);
    $('actionForm').addEventListener('submit', submitAction);
    ['closeAction', 'cancelAction'].forEach(id => $(id).addEventListener('click', () => {
        if (!state.actionBusy) $('actionDialog').close();
    }));
    $('actionDialog').addEventListener('cancel', e => { if (state.actionBusy) e.preventDefault(); });
    $('actionDialog').addEventListener('close', () => { state.pendingAction = null; });
    $('actionQuantity').addEventListener('input', () => {
        $('actionError').hidden = true;
        updateQuantityHint();
    });
}

function selected() { return state.facilities.find(f => f.id === state.selectedFacilityId); }
function canEditWarehouse() {
    return state.role === 'WAREHOUSE' && selected()?.type === 'WAREHOUSE'
        && state.selectedFacilityId === state.actorFacilityId;
}
function syncRequester() { $('requesterName').value = selected()?.name || ''; }
function openRequest(r) { return !['CANCELLED', 'DELIVERED', 'REJECTED'].includes(r.status); }
function unapproved(r) { return Math.max(0, r.quantity - r.approvedQuantity); }
function ownStock(r) { return state.ownInventory.find(i => i.medicineId === r.medicineId); }

async function load() {
    if (!state.selectedFacilityId) return;
    const version = ++state.loadVersion;
    const facilityId = state.selectedFacilityId;
    $('refreshButton').disabled = true;
    $('refreshButton').textContent = 'Refreshing...';
    $('newRequestButton').disabled = true;
    try {
        const [d, own] = await Promise.all([
            get('/dashboard/' + facilityId),
            facilityId === state.actorFacilityId ? Promise.resolve(null) : get('/inventory?facilityId=' + state.actorFacilityId)
        ]);
        if (version !== state.loadVersion) return;
        state.inventory = d.inventory;
        state.ownInventory = own || d.inventory;
        state.requests = d.requests;
        state.loadedFacilityId = facilityId;
        $('dashboardTitle').textContent = d.facilityName;
        $('trackedCount').textContent = d.medicinesTracked;
        $('criticalCount').textContent = d.criticalAlerts;
        $('expiryCount').textContent = d.expiringSoon;
        $('requestCount').textContent = d.openRequests;
        renderInventory(); renderRequests(); renderWarehouseDispatches();
    } catch (e) {
        if (version === state.loadVersion) toast(e.message, true);
    } finally {
        if (version === state.loadVersion) {
            $('refreshButton').disabled = false;
            $('refreshButton').textContent = 'Refresh dashboard';
            $('newRequestButton').disabled = state.loadedFacilityId !== facilityId;
        }
    }
}

function renderInventory() {
    const editable = canEditWarehouse();
    $('inventoryManageHeader').hidden = !editable;
    $('inventoryAccessNote').textContent = editable
        ? 'You can edit your warehouse inventory. Approved transfers keep their stock reserved.'
        : state.role === 'WAREHOUSE'
            ? 'View only. This facility manages its own supply approvals. Switch to your warehouse to edit warehouse stock.'
            : selected()?.type === 'HOSPITAL'
                ? 'Hospital transfers keep the safety reserve plus 50 extra units of each medicine.'
                : 'View only. Your inventory updates when approved supplies are dispatched or received.';
    $('inventoryBody').innerHTML = state.inventory.length ? state.inventory.map(i => `<tr>
        <td><strong>${html(i.medicineName)}</strong><small>${html(i.genericName)}</small></td>
        <td><strong>${i.quantity.toLocaleString()}</strong><small>${html(i.unit)}</small></td>
        <td>${i.dailyConsumption.toFixed(1)}</td><td>${i.daysRemaining >= 999 ? 'Stable' : i.daysRemaining}</td>
        <td>${date(i.expiryDate)}</td><td><span class="risk risk-${slug(i.risk)}">${html(titleCase(i.risk))}</span></td>
        <td>${html(i.recommendation)}<small>Available to offer: ${i.safeTransferQuantity}</small>
        <small>Safety reserve: ${i.safetyFloor}${i.hospitalBuffer ? ' + 50 extra' : ''}. Reserved: ${i.reservedQuantity}.</small></td>
        ${editable ? `<td><button class="mini-button" data-edit="${i.id}" aria-label="Edit ${html(i.medicineName)}">Edit</button></td>` : ''}
        </tr>`).join('') : `<tr><td colspan="${editable ? 8 : 7}" class="loading">No inventory records.</td></tr>`;
}

function renderRequests() {
    $('requestList').innerHTML = state.requests.length ? state.requests.map(r => {
        const own = r.requesterId === state.actorFacilityId && state.role !== 'WAREHOUSE';
        const free = ownStock(r)?.safeTransferQuantity || 0;
        const outstanding = unapproved(r);
        const rows = r.allocations.map(a => {
            const controls = a.sourceId === state.actorFacilityId && openRequest(r);
            let actions = '';
            if (a.state === 'PENDING' && controls) {
                const max = Math.min(a.quantity, outstanding, free);
                actions = `<button class="mini-button" data-action="approve" data-id="${a.id}" ${max < 1 ? 'disabled' : ''}>Approve amount</button>
                    <button class="mini-button" data-action="reject" data-id="${a.id}">Reject</button>`;
                if (max < 1) actions += `<small>${outstanding < 1 ? 'Request fully covered by approvals.' : 'No safe stock available for this approval.'}</small>`;
            }
            if (a.state === 'APPROVED' && controls) actions = `<button class="mini-button" data-action="dispatch" data-id="${a.id}">Dispatch ${a.quantity}</button>`;
            if (a.state === 'DISPATCHED' && own && openRequest(r)) actions = `<button class="mini-button" data-action="deliver" data-id="${a.id}">Confirm receipt</button>`;
            const label = a.state === 'PENDING' ? (outstanding > 0 && openRequest(r) ? 'Proposed' : 'Unused proposal') : titleCase(a.state);
            return `<div class="allocation"><span>${html(a.sourceName)} · ${a.quantity} units · <b>${html(label)}</b></span><span>${actions}</span></div>`;
        }).join('');
        const canOffer = r.priority === 'CRITICAL' && openRequest(r) && state.actorFacilityId !== r.requesterId && outstanding > 0;
        const maxOffer = Math.min(free, outstanding);
        const pendingOwn = r.allocations.some(a => a.sourceId === state.actorFacilityId && a.state === 'PENDING');
        return `<article class="request-card" data-request="${r.id}"><div>
            <h3>${html(r.medicineName)} · ${r.quantity} units</h3>
            <div class="request-meta"><span>${html(r.requesterName)}</span><span>${html(titleCase(r.priority))} priority</span><span>${dateTime(r.createdAt)}</span></div>
            <p>Approved ${r.approvedQuantity} · Dispatched ${r.dispatchedQuantity} · Delivered ${r.deliveredQuantity}</p>
            <p class="request-progress">Not yet approved: ${outstanding}. Awaiting receipt: ${r.remainingQuantity}.</p>
            ${rows || '<p>No safe source proposed yet.</p>'}
            ${r.priority === 'CRITICAL' && openRequest(r) ? '<p class="proposal-note">Proposals are alternatives. Only approvals reserve stock and count toward the request.</p>' : ''}
            <div class="request-controls">${canOffer ? `<button class="mini-button" data-action="offer" data-id="${r.id}" ${maxOffer < 1 ? 'disabled' : ''}>${pendingOwn ? 'Update stock offer' : 'Offer stock'}</button>
                <small>From ${html(state.facilityName)}. Maximum now: ${maxOffer} units.${free === 0 ? ' Your safety reserve and existing approvals must stay protected.' : ''}</small>` : ''}
            ${own && openRequest(r) ? `<button class="mini-button" data-action="cancel" data-id="${r.id}">Cancel request</button>` : ''}</div>
            </div><span class="status">${html(titleCase(r.status))}</span></article>`;
    }).join('') : '<p class="empty-state">No supply requests yet.</p>';
}

function renderWarehouseDispatches() {
    if (state.role !== 'WAREHOUSE') return;
    const entries = state.requests.flatMap(r => r.allocations.filter(a => ['DISPATCHED', 'DELIVERED'].includes(a.state))
        .map(a => ({ request: r, allocation: a }))).sort((x, y) => new Date(y.allocation.updatedAt) - new Date(x.allocation.updatedAt));
    $('dispatchFeed').innerHTML = entries.length ? entries.map(({ request: r, allocation: a }) => `<article class="request-card"><div>
        <h3>${html(r.medicineName)} · ${a.quantity} units</h3><div class="request-meta"><span>From ${html(a.sourceName)}</span>
        <span>To ${html(r.requesterName)}</span><span>Request #${r.id}</span><span>Last change ${dateTime(a.updatedAt)}</span></div></div>
        <span class="status">${html(titleCase(a.state))}</span></article>`).join('') : '<p class="empty-state">No dispatched stock yet.</p>';
}

function editInventory(id) {
    if (!canEditWarehouse() || state.loadedFacilityId !== state.actorFacilityId) return;
    const i = state.inventory.find(x => x.id === id && x.facilityId === state.actorFacilityId);
    if (!i) return;
    state.editing = i.id;
    $('editTitle').textContent = `${i.facilityName} · ${i.medicineName}`;
    $('editQuantity').value = i.quantity; $('editConsumption').value = i.dailyConsumption;
    $('editReorder').value = i.reorderLevel; $('editExpiry').value = i.expiryDate;
    $('editDialog').showModal();
}
function saveInventory(e) {
    e.preventDefault();
    if (!canEditWarehouse()) return;
    const item = state.inventory.find(i => i.id === state.editing && i.facilityId === state.actorFacilityId);
    if (!item || !$('editForm').reportValidity()) return;
    const body = { quantity: Number($('editQuantity').value), dailyConsumption: Number($('editConsumption').value),
        reorderLevel: Number($('editReorder').value), expiryDate: $('editExpiry').value };
    openAction({ title: 'Save warehouse inventory?', description: 'Review the changes for your warehouse.',
        details: [['Facility', item.facilityName], ['Medicine', item.medicineName],
            ['Quantity', `${item.quantity} to ${body.quantity}`], ['Daily consumption', `${item.dailyConsumption} to ${body.dailyConsumption}`],
            ['Reorder level', `${item.reorderLevel} to ${body.reorderLevel}`], ['Expiry date', date(body.expiryDate)]],
        confirmLabel: 'Save changes', success: 'Warehouse inventory updated',
        onConfirm: () => send('/inventory/' + item.id, 'PUT', body), afterSuccess: () => $('editDialog').close() });
}

function openAction(config) {
    if ($('actionDialog').open || state.actionBusy) return;
    state.pendingAction = config;
    $('actionTitle').textContent = config.title;
    $('actionDescription').textContent = config.description;
    $('actionDetails').innerHTML = config.details.map(([key, value]) => `<div><dt>${html(key)}</dt><dd>${html(value)}</dd></div>`).join('');
    $('actionQuantityLabel').hidden = config.max === undefined;
    $('actionQuantity').disabled = config.max === undefined;
    $('actionQuantity').value = config.value ?? config.max ?? '';
    $('actionQuantity').max = config.max ?? 100000;
    $('actionError').hidden = true;
    $('confirmAction').textContent = config.confirmLabel || 'Confirm';
    $('confirmAction').disabled = config.max !== undefined && config.max < 1;
    updateQuantityHint();
    $('actionDialog').showModal();
    (config.max === undefined ? $('cancelAction') : $('actionQuantity')).focus();
}
function updateQuantityHint() {
    const config = state.pendingAction;
    if (!config) return;
    const quantity = Number($('actionQuantity').value);
    $('actionHint').textContent = config.hintFor ? config.hintFor(quantity) : config.hint || '';
}
async function submitAction(e) {
    e.preventDefault();
    const config = state.pendingAction;
    if (!config || state.actionBusy) return;
    const quantity = Number($('actionQuantity').value);
    if (config.max !== undefined && (!Number.isInteger(quantity) || quantity < 1 || quantity > config.max)) {
        $('actionError').textContent = `Enter a whole number from 1 to ${config.max}.`;
        $('actionError').hidden = false; return;
    }
    state.actionBusy = true;
    ['confirmAction', 'cancelAction', 'closeAction', 'actionQuantity'].forEach(id => $(id).disabled = true);
    $('confirmAction').textContent = 'Saving...';
    $('actionError').hidden = true;
    try {
        await config.onConfirm(quantity);
        config.afterSuccess?.();
        $('actionDialog').close();
        toast(config.success || 'Action recorded');
        await load();
    } catch (err) {
        $('actionError').textContent = err.message;
        $('actionError').hidden = false;
    } finally {
        state.actionBusy = false;
        ['confirmAction', 'cancelAction', 'closeAction'].forEach(id => $(id).disabled = false);
        $('actionQuantity').disabled = config.max === undefined;
        $('confirmAction').textContent = config.confirmLabel || 'Confirm';
    }
}

async function action(kind, id) {
    if ($('actionDialog').open || state.actionBusy) return;
    const r = ['offer', 'cancel'].includes(kind) ? state.requests.find(x => x.id === id)
        : state.requests.find(x => x.allocations.some(a => a.id === id));
    if (!r) return;
    const a = r.allocations.find(x => x.id === id);
    const details = [['Medicine', r.medicineName], ['Receiving facility', r.requesterName]];
    if (kind === 'offer') {
        const sourceId = state.actorFacilityId; // Never use a facility the warehouse is only viewing.
        const options = await get(`/requests/${id}/offer-options?sourceId=${sourceId}`);
        if (options.maxOfferQuantity < 1) { toast('No safe offer is available, or the request is already covered by approvals.', true); await load(); return; }
        openAction({ title: options.proposedQuantity ? 'Update stock offer' : 'Offer stock for a critical request',
            description: 'Choose the amount to propose. Review and approve your proposal before dispatching it.',
            details: [['Supplying facility', options.sourceName], ...details, ['Current stock', options.currentQuantity],
                ['Safety reserve', options.safetyFloor], ['Extra hospital reserve', options.hospitalBuffer],
                ['Already reserved', options.reservedQuantity], ['Maximum offer', options.maxOfferQuantity]],
            max: options.maxOfferQuantity, value: Math.min(options.proposedQuantity || options.maxOfferQuantity, options.maxOfferQuantity),
            hintFor: n => Number.isInteger(n) && n >= 1 && n <= options.maxOfferQuantity
                ? `After this transfer and other approved transfers: ${options.currentQuantity - options.reservedQuantity - n} units remain. Required reserve: ${options.safetyFloor + options.hospitalBuffer}.`
                : `Choose 1 to ${options.maxOfferQuantity} units.`,
            confirmLabel: 'Save offer', success: 'Offer saved. Approve the proposal when ready to send it.',
            onConfirm: quantity => send(`/requests/${id}/offers`, 'POST', { sourceId, quantity }) });
        return;
    }
    if (kind === 'approve') {
        const i = ownStock(r);
        const max = Math.min(a.quantity, unapproved(r), i?.safeTransferQuantity || 0);
        if (max < 1) { toast('No safe quantity remains for approval. Refresh the dashboard.', true); return; }
        openAction({ title: 'Approve supply quantity', description: 'This reserves stock at your facility until dispatch.',
            details: [['Supplying facility', a.sourceName], ...details, ['Maximum approval', max],
                ['Safety reserve', i.safetyFloor], ['Extra hospital reserve', i.hospitalBuffer]],
            max, value: max, confirmLabel: 'Approve quantity', success: 'Supply quantity approved and reserved',
            hintFor: n => Number.isInteger(n) && n >= 1 && n <= max
                ? `After all approved transfers: ${i.quantity - i.reservedQuantity - n} units remain. Required reserve: ${i.safetyFloor + i.hospitalBuffer}.`
                : `Choose 1 to ${max} units.`,
            onConfirm: quantity => send(`/allocations/${id}/approve`, 'POST', { quantity }) });
        return;
    }
    const labels = { dispatch: ['Dispatch medicine stock?', 'Dispatch subtracts the approved amount from the supplying facility.', 'Dispatch stock'],
        deliver: ['Confirm medicine receipt?', 'Confirm only after this supply has arrived. The receiving inventory will increase.', 'Confirm receipt'],
        reject: ['Reject this proposal?', 'This unapproved proposal will be marked as rejected.', 'Reject proposal'],
        cancel: ['Cancel this request?', 'Unsent approvals will release their reserved stock. A supply already in transit must be received first.', 'Cancel request'] };
    if (!labels[kind]) return;
    const [title, description, confirmLabel] = labels[kind];
    if (a && kind !== 'cancel') details.unshift(['Supplying facility', a.sourceName], ['Quantity', a.quantity]);
    const i = ownStock(r);
    const hint = kind === 'dispatch' && i
        ? `Required reserve: ${i.safetyFloor}${i.hospitalBuffer ? ' + 50 extra hospital units' : ''}. The server checks current stock and every other approved transfer again.` : '';
    openAction({ title, description, details, confirmLabel, hint,
        success: kind === 'deliver' ? 'Receipt confirmed. Inventory updated.' : titleCase(kind) + ' completed',
        onConfirm: () => send(kind === 'cancel' ? `/requests/${id}/cancel` : `/allocations/${id}/${kind}`, 'POST') });
}

async function suggest() {
    const requester = state.selectedFacilityId;
    const medicine = Number($('medicineId').value);
    const priority = $('priority').value;
    if (!requester || !medicine) return;
    try {
        let list = await get(`/suggestions?requesterId=${requester}&medicineId=${medicine}`);
        if (requester !== state.selectedFacilityId || medicine !== Number($('medicineId').value) || priority !== $('priority').value) return;
        if (priority !== 'CRITICAL') list = list.filter(s => state.facilities.find(f => f.id === s.sourceFacilityId)?.type === 'WAREHOUSE');
        $('sourceSuggestion').textContent = list.length ? list.map(x => `${x.sourceFacilityName}: ${x.safeTransferQuantity} units available to offer`).join('. ')
            : 'No safe transferable stock is currently available. You can still raise a request.';
    } catch (e) { $('sourceSuggestion').textContent = e.message; }
}
function createRequest(e) {
    e.preventDefault();
    if (state.role === 'WAREHOUSE' || state.selectedFacilityId !== state.actorFacilityId || !$('requestForm').reportValidity()) return;
    const body = { requesterId: state.actorFacilityId, medicineId: Number($('medicineId').value), quantity: Number($('quantity').value),
        priority: $('priority').value, notes: $('notes').value.trim() };
    const med = state.medicines.find(m => m.id === body.medicineId);
    openAction({ title: 'Send supply request?', description: 'Review the facility and quantity before sending.',
        details: [['Requesting facility', state.facilityName], ['Medicine', med?.name || ''], ['Quantity', body.quantity], ['Priority', titleCase(body.priority)]],
        confirmLabel: 'Send request', success: 'Supply request created', onConfirm: () => send('/requests', 'POST', body),
        afterSuccess: () => { $('requestDialog').close(); $('requestForm').reset(); syncRequester(); } });
}
async function logout() {
    try {
        const response = await fetch('/logout', { method: 'POST', headers: { 'X-XSRF-TOKEN': csrf() } });
        if (response.ok || response.redirected) location.href = '/login?logout';
        else toast('Logout failed. Refresh and try again.', true);
    } catch (_) { toast('Cannot reach the server. Check that MediHive is running.', true); }
}
function csrf() { return decodeURIComponent(document.cookie.split('; ').find(x => x.startsWith('XSRF-TOKEN='))?.slice(11) || ''); }
async function get(path) { return handle(await fetch(API + path)); }
async function send(path, method, body) {
    return handle(await fetch(API + path, { method, headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrf() },
        body: body === undefined ? undefined : JSON.stringify(body) }));
}
async function handle(response) {
    if (response.redirected && response.url.includes('/login')) { location.href = '/login'; throw Error('Your session expired. Please sign in again.'); }
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw Error(body.message || (response.status === 403
        ? 'This action is not allowed for this account, or your session needs a refresh.' : `Request failed (${response.status}). Refresh and try again.`));
    return body;
}
function toast(msg, error = false) {
    const t = $('toast'); t.textContent = msg; t.style.background = error ? '#b51f3b' : '#092a35';
    t.classList.add('show'); clearTimeout(toastTimer); toastTimer = setTimeout(() => t.classList.remove('show'), 6000);
}
function html(x) { return String(x ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]); }
function slug(x) { return String(x).toLowerCase().replaceAll('_', '-'); }
function titleCase(x) { return String(x).toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, c => c.toUpperCase()); }
function date(x) { return new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', year: 'numeric' }).format(new Date(x + 'T00:00:00')); }
function dateTime(x) { return new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }).format(new Date(x)); }
