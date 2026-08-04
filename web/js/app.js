import { addDocument, deleteDocument, updateStatus, listPending, listUploaded } from './db.js?v=8';
import { openScanner } from './scanner.js?v=8';

// ---------------- constants / helpers ----------------
const DEMO_USER = 'hdfc';
const DEMO_PASS = '123';
const app = document.getElementById('app');
const modalRoot = document.getElementById('modal-root');

const session = {
    get() { try { return JSON.parse(localStorage.getItem('session')); } catch { return null; } },
    set(v) { localStorage.setItem('session', JSON.stringify(v)); },
    clear() { localStorage.removeItem('session'); },
};

function isMobile() {
    const ua = navigator.userAgent || '';
    const uaMobile = /Android|iPhone|iPad|iPod|Opera Mini|IEMobile|Mobile|Silk/i.test(ua);
    const coarse = window.matchMedia && window.matchMedia('(pointer: coarse)').matches;
    const touch = navigator.maxTouchPoints > 1;
    return uaMobile || (coarse && touch);
}
const hasCamera = () => !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia);

function readableSize(bytes) {
    if (!bytes) return '0 B';
    const u = ['B', 'KB', 'MB', 'GB'];
    const i = Math.min(u.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)));
    return (bytes / Math.pow(1024, i)).toFixed(i ? 1 : 0) + ' ' + u[i];
}
function fmtDate(ts) {
    return new Date(ts).toLocaleString(undefined, {
        day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
    });
}
const esc = (s) => String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

function toast(msg) {
    const t = document.createElement('div');
    t.className = 'toast'; t.textContent = msg;
    modalRoot.appendChild(t);
    setTimeout(() => t.remove(), 2200);
}

function modal({ icon, title, body, actions }) {
    const back = document.createElement('div');
    back.className = 'modal-backdrop';
    back.innerHTML = `
        <div class="modal">
            ${icon ? `<div class="m-ico">${icon}</div>` : ''}
            ${title ? `<h3>${esc(title)}</h3>` : ''}
            ${body ? `<p>${body}</p>` : ''}
            <div class="btn-row modal-actions"></div>
        </div>`;
    const row = back.querySelector('.modal-actions');
    (actions || [{ label: 'OK' }]).forEach(a => {
        const b = document.createElement('button');
        b.className = 'btn ' + (a.variant || 'ghost');
        b.textContent = a.label;
        b.onclick = () => { back.remove(); a.onClick && a.onClick(); };
        row.appendChild(b);
    });
    back.addEventListener('click', (e) => { if (e.target === back) back.remove(); });
    modalRoot.appendChild(back);
}

// ---------------- router ----------------
function navigate(hash) { location.hash = hash; }
function parseRoute() {
    const h = location.hash.replace(/^#\/?/, '');
    const [name, arg] = h.split('/');
    return { name: name || '', arg };
}

async function render() {
    const { name, arg } = parseRoute();
    const s = session.get();

    if (!s && name !== 'login') return navigate('/login');
    if (s && (name === '' || name === 'login')) return navigate('/search');

    switch (name) {
        case 'login': return renderLogin();
        case 'search': return renderSearch();
        case 'upload': return renderUpload(arg);
        case 'uploaded': return renderUploaded(arg);
        default: return navigate(s ? '/search' : '/login');
    }
}

function appbar({ title, subtitle, back, actions }) {
    return `
    <header class="appbar"><div class="bar-inner">
        ${back ? `<button class="icon-btn" data-nav-back aria-label="Back">←</button>` : ''}
        <div><h1>${esc(title)}</h1>${subtitle ? `<div class="subtitle">${esc(subtitle)}</div>` : ''}</div>
        <span class="spacer"></span>
        ${actions || ''}
    </div></header>`;
}

// ---------------- Login ----------------
function renderLogin() {
    app.innerHTML = `
    <div class="screen centered">
        <div class="brand-logo">🏦</div>
        <div class="title">Welcome Back</div>
        <p class="subtitle-muted">Sign in to continue</p>
        <form id="login-form" style="width:100%;max-width:340px;margin-top:18px">
            <div class="field">
                <label>Username</label>
                <div class="input-wrap">
                    <span class="lead">👤</span>
                    <input type="text" id="username" autocomplete="username" />
                </div>
                <div class="field-error" id="err-username"></div>
            </div>
            <div class="field">
                <label>Password</label>
                <div class="input-wrap">
                    <span class="lead">🔒</span>
                    <input type="password" id="password" autocomplete="current-password" />
                    <button type="button" class="trail" id="toggle-pass">👁️</button>
                </div>
                <div class="field-error" id="err-password"></div>
            </div>
            <div class="field-error" id="err-general" style="text-align:center"></div>
            <button class="btn" type="submit" id="login-btn">Login</button>
        </form>
        <p class="hint">Demo — Username: <b>hdfc</b> &nbsp;•&nbsp; Password: <b>123</b></p>
    </div>`;

    const form = document.getElementById('login-form');
    document.getElementById('toggle-pass').onclick = () => {
        const p = document.getElementById('password');
        p.type = p.type === 'password' ? 'text' : 'password';
    };
    form.onsubmit = (e) => {
        e.preventDefault();
        const u = document.getElementById('username').value.trim();
        const p = document.getElementById('password').value;
        document.getElementById('err-username').textContent = u ? '' : 'Username is required.';
        document.getElementById('err-password').textContent = p ? '' : 'Password is required.';
        document.getElementById('err-general').textContent = '';
        if (!u || !p) return;

        const btn = document.getElementById('login-btn');
        btn.disabled = true; btn.innerHTML = '<span class="spinner"></span>';
        setTimeout(() => {
            if (u === DEMO_USER && p === DEMO_PASS) {
                session.set({ token: 'web-' + Date.now(), username: u });
                navigate('/search');
            } else {
                btn.disabled = false; btn.textContent = 'Login';
                document.getElementById('err-general').textContent = 'Invalid Username or Password.';
            }
        }, 500);
    };
}

// ---------------- Search ----------------
function renderSearch() {
    app.innerHTML = `
    ${appbar({ title: 'Search Application', actions: `<button class="icon-btn" id="logout" title="Logout">⏻</button>` })}
    <div class="screen">
        <p class="subtitle-muted">Enter the application number to continue.</p>
        <div class="field" style="margin-top:12px">
            <label>Application Number</label>
            <div class="input-wrap">
                <span class="lead">#️⃣</span>
                <input type="text" id="appno" inputmode="text" autocapitalize="characters" placeholder="e.g. HDFC1023" />
            </div>
            <div class="field-error" id="err-appno"></div>
        </div>
        <button class="btn" id="search-btn">Search</button>
    </div>`;

    document.getElementById('logout').onclick = () => {
        session.clear(); navigate('/login');
    };
    const input = document.getElementById('appno');
    const err = document.getElementById('err-appno');
    input.addEventListener('input', () => {
        const cleaned = input.value.replace(/[^a-zA-Z0-9]/g, '');
        if (cleaned !== input.value) { input.value = cleaned; err.textContent = 'Only alphanumeric characters are allowed.'; }
        else err.textContent = '';
    });
    document.getElementById('search-btn').onclick = () => {
        const v = input.value.trim();
        if (!v) { err.textContent = 'Application Number is required.'; return; }
        const btn = document.getElementById('search-btn');
        btn.disabled = true; btn.innerHTML = '<span class="spinner"></span>';
        setTimeout(() => navigate('/upload/' + encodeURIComponent(v)), 400);
    };
}

// ---------------- Upload ----------------
async function renderUpload(appNo) {
    appNo = decodeURIComponent(appNo || '');
    const pending = await listPending(appNo);

    app.innerHTML = `
    ${appbar({ title: 'Upload Documents', subtitle: 'App No: ' + appNo, back: true })}
    <div class="screen">
        <div class="option-grid">
            <div class="option-card" id="opt-gallery"><div class="ico">🖼️</div><div class="label">Upload from Mobile</div></div>
            <div class="option-card" id="opt-scan"><div class="ico">📷</div><div class="label">Scan Document</div></div>
        </div>
        <div class="section-title">Documents <span class="badge">${pending.length}</span></div>
        <div class="doc-list" id="doc-list"></div>
        <div class="screen-footer">
            <button class="btn" id="upload-btn" ${pending.length ? '' : 'disabled'}>Upload</button>
        </div>
    </div>
    <input type="file" id="file-input" accept="image/*,application/pdf" multiple hidden />
    <input type="file" id="cam-input" accept="image/*" capture="environment" hidden />`;

    app.querySelector('[data-nav-back]').onclick = () => navigate('/search');
    renderDocList('doc-list', pending, { removable: true, onChange: () => renderUpload(encodeURIComponent(appNo)) });

    // ---- Upload from device (images + PDF, multiple) ----
    const fileInput = document.getElementById('file-input');
    document.getElementById('opt-gallery').onclick = () => fileInput.click();
    fileInput.onchange = async () => {
        const files = [...fileInput.files];
        for (const f of files) {
            const isPdf = f.type === 'application/pdf';
            const isImg = f.type.startsWith('image/');
            if (!isPdf && !isImg) { toast('Unsupported file type.'); continue; }
            await addDocument({
                appNo, name: f.name, mime: f.type, size: f.size,
                type: isPdf ? 'pdf' : 'image', blob: f,
            });
        }
        fileInput.value = '';
        renderUpload(encodeURIComponent(appNo));
    };

    // ---- Scan ----
    document.getElementById('opt-scan').onclick = () => onScan(appNo);
    const camInput = document.getElementById('cam-input');
    camInput.onchange = async () => {
        for (const f of camInput.files) {
            await addDocument({ appNo, name: 'Scan_' + Date.now() + '.jpg', mime: f.type || 'image/jpeg', size: f.size, type: 'scan', blob: f });
        }
        camInput.value = '';
        renderUpload(encodeURIComponent(appNo));
    };

    // ---- Upload action ----
    document.getElementById('upload-btn').onclick = () => doUpload(appNo, pending);
}

async function onScan(appNo) {
    // Desktop: scanner is a mobile-only capability → show popup ("scanner finding").
    if (!isMobile()) {
        modal({
            icon: '📱',
            title: 'Scanner available on mobile',
            body: 'Document scanning uses your device camera and is available on <b>mobile devices</b> only.<br><br>Open this page on your phone to scan documents. You can still upload files from this computer.',
            actions: [{ label: 'Got it', variant: 'ghost' }],
        });
        return;
    }
    // Mobile with camera API → full in-browser scanner.
    if (hasCamera()) {
        try {
            const pages = await openScanner();
            for (const blob of pages) {
                await addDocument({ appNo, name: 'Scan_' + Date.now() + '_' + Math.random().toString(36).slice(2, 6) + '.jpg', mime: 'image/jpeg', size: blob.size, type: 'scan', blob });
            }
            renderUpload(encodeURIComponent(appNo));
        } catch (err) {
            // Camera couldn't start → explain, and offer the native camera as fallback.
            const name = (err && err.name) || 'Error';
            const msg = name === 'NotAllowedError'
                ? 'Camera permission was denied. Enable camera access for this site in your browser settings, then try again.'
                : name === 'NotFoundError'
                    ? 'No camera was found on this device.'
                    : `Camera couldn't start (${name}). You can use your phone's camera app instead.`;
            modal({
                icon: '📷', title: 'Camera unavailable', body: msg,
                actions: [
                    { label: 'Use phone camera', variant: 'ghost', onClick: () => document.getElementById('cam-input').click() },
                    { label: 'Cancel', variant: 'ghost' },
                ],
            });
        }
        return;
    }
    // Mobile without getUserMedia → native camera capture input.
    document.getElementById('cam-input').click();
}

async function doUpload(appNo, pending) {
    if (!pending.length) { toast('Please add at least one document to upload.'); return; }
    if (!navigator.onLine) {
        modal({ icon: '📶', title: 'No internet connection', body: 'Your documents are saved and will be uploaded automatically when you are back online.' });
        return;
    }
    const btn = document.getElementById('upload-btn');
    btn.disabled = true;
    const footer = btn.parentElement;
    footer.insertAdjacentHTML('afterbegin', `
        <div class="progress-wrap">
            <div class="progress-label" id="p-label">Uploading… 0%</div>
            <div class="progress-track"><div class="progress-bar" id="p-bar"></div></div>
        </div>`);
    const bar = document.getElementById('p-bar');
    const label = document.getElementById('p-label');

    for (let i = 0; i < pending.length; i++) {
        await new Promise(r => setTimeout(r, 600));
        await updateStatus(pending[i].id, 'uploaded');
        const pct = Math.round(((i + 1) / pending.length) * 100);
        bar.style.width = pct + '%';
        label.textContent = `Uploading… ${pct}%`;
    }
    setTimeout(() => renderUploadSuccess(appNo), 400);
}

function renderUploadSuccess(appNo) {
    app.innerHTML = `
    ${appbar({ title: 'Upload Complete' })}
    <div class="screen centered">
        <div class="success-check">✓</div>
        <div class="title">Documents uploaded successfully.</div>
        <p class="subtitle-muted">What would you like to do next?</p>
        <div style="width:100%;max-width:340px;margin-top:20px;display:flex;flex-direction:column;gap:12px">
            <button class="btn" id="view-btn">View Uploaded Documents</button>
            <button class="btn secondary" id="more-btn">Upload More</button>
        </div>
    </div>`;
    document.getElementById('view-btn').onclick = () => navigate('/uploaded/' + encodeURIComponent(appNo));
    document.getElementById('more-btn').onclick = () => navigate('/upload/' + encodeURIComponent(appNo));
}

// ---------------- Uploaded list ----------------
async function renderUploaded(appNo) {
    appNo = decodeURIComponent(appNo || '');
    const docs = await listUploaded(appNo);
    app.innerHTML = `
    ${appbar({ title: 'Uploaded Documents', subtitle: 'App No: ' + appNo, back: true })}
    <div class="screen">
        <div class="doc-list" id="doc-list"></div>
    </div>`;
    app.querySelector('[data-nav-back]').onclick = () => navigate('/upload/' + encodeURIComponent(appNo));
    renderDocList('doc-list', docs, {
        removable: true, previewable: true, showDate: true,
        onChange: () => renderUploaded(encodeURIComponent(appNo)),
    });
}

// ---------------- shared document list ----------------
function renderDocList(containerId, docs, opts = {}) {
    const box = document.getElementById(containerId);
    if (!docs.length) {
        box.innerHTML = `<div class="empty"><div><div class="ico">🗂️</div><p>No documents yet.<br>Scan or upload to get started.</p></div></div>`;
        return;
    }
    box.innerHTML = '';
    docs.forEach(d => {
        const url = d.type === 'pdf' ? null : URL.createObjectURL(d.blob);
        const card = document.createElement('div');
        card.className = 'doc-card';
        card.innerHTML = `
            <div class="doc-thumb">${url ? `<img src="${url}" alt="">` : '📄'}</div>
            <div class="doc-meta">
                <div class="doc-name">${esc(d.name)}</div>
                <div class="doc-sub">${readableSize(d.size)}${opts.showDate ? ' • ' + fmtDate(d.createdAt) : ''}</div>
                <div class="doc-status ${d.status === 'uploaded' ? 'status-uploaded' : 'status-pending'}">${d.status === 'uploaded' ? 'Uploaded' : 'Pending'}</div>
            </div>
            <div class="doc-actions"></div>`;
        const actions = card.querySelector('.doc-actions');
        if (opts.previewable) {
            const p = iconButton('👁️', 'Preview', () => previewDoc(d));
            actions.appendChild(p);
        }
        if (opts.removable) {
            const r = iconButton('🗑️', 'Delete', () => {
                modal({
                    icon: '🗑️', title: 'Delete document?', body: esc(d.name),
                    actions: [
                        { label: 'Cancel', variant: 'ghost' },
                        { label: 'Delete', variant: 'danger', onClick: async () => { await deleteDocument(d.id); opts.onChange && opts.onChange(); } },
                    ],
                });
            });
            actions.appendChild(r);
        }
        box.appendChild(card);
    });
}

function iconButton(label, title, onClick) {
    const b = document.createElement('button');
    b.className = 'icon-btn'; b.title = title; b.textContent = label;
    b.style.color = 'var(--on-surface-muted)';
    b.onclick = onClick;
    return b;
}

function previewDoc(d) {
    const url = URL.createObjectURL(d.blob);
    if (d.type === 'pdf') { window.open(url, '_blank'); return; }
    const back = document.createElement('div');
    back.className = 'modal-backdrop';
    back.innerHTML = `<div class="modal" style="max-width:92%;padding:12px">
        <img src="${url}" style="max-width:100%;max-height:70vh;border-radius:12px" alt="${esc(d.name)}">
        <div style="margin-top:10px;font-weight:600">${esc(d.name)}</div>
        <div class="btn-row" style="margin-top:12px"><button class="btn ghost" data-close>Close</button></div>
    </div>`;
    back.addEventListener('click', (e) => { if (e.target === back || e.target.hasAttribute('data-close')) back.remove(); });
    modalRoot.appendChild(back);
}

// ---------------- boot ----------------
window.addEventListener('hashchange', render);
window.addEventListener('DOMContentLoaded', render);
render();
