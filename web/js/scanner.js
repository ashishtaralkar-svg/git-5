// In-browser document scanner — pure JavaScript, no external libraries.
// Flow (Adobe Scan style):
//   1. Live camera → tap the shutter (or the preview) to capture.
//   2. Crop screen: the photo with 4 draggable corner handles, auto-positioned
//      to the detected document edges. Drag to adjust.
//   3. Crop → perspective-corrects & deskews the page (JS homography warp).
//   4. Enhance: Original / Color / B&W, then keep. Multi-page, torch, retake.
// Everything runs locally with no 8MB library download, so it works reliably on
// mobile where OpenCV.js often failed to load.

const MAX_OUT_EDGE = 1500;

export function openScanner() {
    return new Promise((resolve, reject) => {
        const pages = [];
        let stream = null, track = null, torchOn = false, videoReady = false;
        let mode = 'live';                 // 'live' | 'crop' | 'review'
        let captured = null;               // full-res captured canvas
        let corners = null;                // [tl,tr,br,bl] in captured-image px
        let originalImageData = null;      // cropped image data for filters
        let dragIdx = -1;
        const cleanupHooks = [];

        const root = document.createElement('div');
        root.className = 'scanner';
        root.innerHTML = `
            <div class="scan-topbar">
                <button class="icon-btn" data-act="close" aria-label="Close">✕</button>
                <span class="title" id="scan-status">Starting camera…</span>
                <span class="spacer"></span>
                <button class="scan-side-btn" data-act="torch" aria-label="Flash">⚡</button>
            </div>

            <!-- LIVE -->
            <video autoplay playsinline muted></video>
            <div class="hitlayer live-only"></div>
            <button class="tap-start hidden" data-act="start">▶<span>Tap to start camera</span></button>

            <!-- CROP -->
            <canvas class="crop-img hidden"></canvas>
            <svg class="crop-svg hidden" xmlns="http://www.w3.org/2000/svg" preserveAspectRatio="xMidYMid meet">
                <polygon class="crop-poly" points=""></polygon>
                <circle class="ch" data-i="0" r="0"></circle>
                <circle class="ch" data-i="1" r="0"></circle>
                <circle class="ch" data-i="2" r="0"></circle>
                <circle class="ch" data-i="3" r="0"></circle>
            </svg>

            <!-- REVIEW -->
            <canvas class="preview hidden"></canvas>
            <div class="filter-bar review hidden">
                <button class="filter-chip" data-filter="original">Original</button>
                <button class="filter-chip active" data-filter="color">Color</button>
                <button class="filter-chip" data-filter="bw">B&amp;W</button>
            </div>

            <!-- CONTROLS -->
            <div class="scan-controls c-live">
                <span style="width:52px"></span>
                <button class="shutter" data-act="capture" aria-label="Capture"></button>
                <button class="scan-side-btn" data-act="done" title="Done">✓<span class="count"></span></button>
            </div>
            <div class="scan-controls c-crop hidden">
                <button class="scan-side-btn" data-act="retake" title="Retake">↺</button>
                <button class="btn-pill" data-act="crop">Crop</button>
                <button class="scan-side-btn" data-act="full" title="Use full photo">▢</button>
            </div>
            <div class="scan-controls c-review hidden">
                <button class="scan-side-btn" data-act="recrop" title="Adjust crop">✎</button>
                <button class="shutter" data-act="keep" aria-label="Keep" style="background:#1a7f37;border-color:#fff"></button>
                <span style="width:52px"></span>
            </div>`;
        document.getElementById('modal-root').appendChild(root);

        const $ = (s) => root.querySelector(s);
        const video = $('video');
        const hit = $('.hitlayer');
        const cropImg = $('.crop-img');
        const cropCtx = cropImg.getContext('2d');
        const svg = $('.crop-svg');
        const poly = $('.crop-poly');
        const handles = [...root.querySelectorAll('circle.ch')];
        const review = $('.preview');
        const rctx = review.getContext('2d', { willReadFrequently: true });
        const statusEl = $('#scan-status');
        const setStatus = (t) => { statusEl.textContent = t; };
        const showEl = (sel, on) => root.querySelectorAll(sel).forEach(e => e.classList.toggle('hidden', !on));
        const updateCount = () => { root.querySelector('.count').textContent = pages.length ? ` ${pages.length}` : ''; };

        function setMode(m) {
            mode = m;
            showEl('video, .hitlayer', m === 'live');
            showEl('.crop-img, .crop-svg', m === 'crop');
            showEl('.preview, .filter-bar.review', m === 'review');
            showEl('.c-live', m === 'live');
            showEl('.c-crop', m === 'crop');
            showEl('.c-review', m === 'review');
        }

        // ---------- camera ----------
        function tryPlay() { video.muted = true; video.playsInline = true; const p = video.play(); if (p && p.catch) p.catch(() => {}); }
        function cleanup(result) {
            if (stream) stream.getTracks().forEach(t => t.stop());
            window.removeEventListener('resize', onResize);
            cleanupHooks.forEach(fn => { try { fn(); } catch (_) {} });
            root.remove();
            resolve(result);
        }
        function onResize() { if (mode === 'crop') drawCrop(); }

        async function start() {
            updateCount();
            setStatus('Starting camera…');
            const onPlaying = () => { videoReady = true; showEl('.tap-start', false); setStatus('Tap the shutter to capture'); };
            video.addEventListener('playing', onPlaying);
            video.addEventListener('canplay', () => { tryPlay(); if (video.videoWidth) onPlaying(); });
            video.addEventListener('loadedmetadata', tryPlay);
            try {
                stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false });
                video.srcObject = stream;
                track = stream.getVideoTracks()[0];
                tryPlay();
                setTimeout(() => { if (!videoReady && mode === 'live') { showEl('.tap-start', true); setStatus('Tap “Start camera”'); } }, 1200);
            } catch (err) { root.remove(); reject(err); }
        }

        // ---------- capture → crop ----------
        function capture() {
            const vw = video.videoWidth, vh = video.videoHeight;
            if (!vw || !vh) { setStatus('Camera not ready yet…'); return; }
            captured = document.createElement('canvas');
            captured.width = vw; captured.height = vh;
            captured.getContext('2d').drawImage(video, 0, 0, vw, vh);
            corners = detectCorners(captured);
            enterCrop();
        }

        function enterCrop() {
            setStatus('Drag the corners to the document, then Crop');
            cropImg.width = captured.width; cropImg.height = captured.height;
            cropCtx.drawImage(captured, 0, 0);
            svg.setAttribute('viewBox', `0 0 ${captured.width} ${captured.height}`);
            const r = Math.max(captured.width, captured.height) * 0.03;
            handles.forEach(h => h.setAttribute('r', r));
            setMode('crop');
            drawCrop();
        }

        function drawCrop() {
            poly.setAttribute('points', corners.map(p => `${p.x},${p.y}`).join(' '));
            handles.forEach((h, i) => { h.setAttribute('cx', corners[i].x); h.setAttribute('cy', corners[i].y); });
        }

        function svgPoint(e) {
            const p = svg.createSVGPoint();
            p.x = e.clientX; p.y = e.clientY;
            const m = svg.getScreenCTM();
            return m ? p.matrixTransform(m.inverse()) : { x: 0, y: 0 };
        }
        handles.forEach((h) => {
            h.addEventListener('pointerdown', (e) => {
                e.preventDefault();
                dragIdx = +h.dataset.i;
                try { h.setPointerCapture(e.pointerId); } catch (_) {}
            });
            h.addEventListener('pointermove', (e) => {
                if (dragIdx < 0) return;
                const p = svgPoint(e);
                corners[dragIdx] = {
                    x: Math.max(0, Math.min(captured.width, p.x)),
                    y: Math.max(0, Math.min(captured.height, p.y)),
                };
                drawCrop();
            });
            const end = () => { dragIdx = -1; };
            h.addEventListener('pointerup', end);
            h.addEventListener('pointercancel', end);
        });

        function doCrop(useFull) {
            const quad = useFull
                ? [{ x: 0, y: 0 }, { x: captured.width, y: 0 }, { x: captured.width, y: captured.height }, { x: 0, y: captured.height }]
                : orderPoints(corners);
            const out = warp(captured, quad);
            review.width = out.width; review.height = out.height;
            rctx.drawImage(out, 0, 0);
            originalImageData = rctx.getImageData(0, 0, out.width, out.height);
            applyFilter('color');
            root.querySelectorAll('.filter-chip').forEach(c => c.classList.toggle('active', c.dataset.filter === 'color'));
            setMode('review');
            setStatus('Choose a filter, then keep');
        }

        function keep() {
            review.toBlob((blob) => { if (blob) { pages.push(blob); updateCount(); } backToLive(); }, 'image/jpeg', 0.85);
        }
        function backToLive() { captured = null; corners = null; setMode('live'); setStatus(videoReady ? 'Tap the shutter to capture' : 'Starting camera…'); }

        function applyFilter(kind) {
            if (!originalImageData) return;
            const src = originalImageData.data;
            const out = rctx.createImageData(review.width, review.height);
            if (kind === 'original') out.data.set(src);
            else if (kind === 'color') autoContrast(src, out.data);
            else if (kind === 'bw') grayscaleOtsu(src, out.data);
            rctx.putImageData(out, 0, 0);
        }

        async function toggleTorch() {
            if (!track) return;
            try {
                const caps = track.getCapabilities ? track.getCapabilities() : {};
                if (!caps.torch) { setStatus('Flash not supported'); return; }
                torchOn = !torchOn;
                await track.applyConstraints({ advanced: [{ torch: torchOn }] });
                root.querySelector('[data-act=torch]').classList.toggle('active', torchOn);
            } catch (_) {}
        }

        // ---------- input wiring (direct, per-element) ----------
        function actOn(act) {
            switch (act) {
                case 'start': tryPlay(); break;
                case 'capture': if (mode === 'live') capture(); break;
                case 'crop': if (mode === 'crop') doCrop(false); break;
                case 'full': if (mode === 'crop') doCrop(true); break;
                case 'recrop': if (mode === 'review') enterCrop(); break;
                case 'retake': backToLive(); break;
                case 'keep': if (mode === 'review') keep(); break;
                case 'torch': toggleTorch(); break;
                case 'done':
                case 'close': cleanup(pages); break;
            }
        }
        function bind(el, fn) {
            if (!el) return;
            let last = 0;
            const g = (e) => { const n = Date.now(); if (n - last < 400) return; last = n; if (e.cancelable) e.preventDefault(); fn(e); };
            el.addEventListener('pointerup', g);
            el.addEventListener('click', g);
        }
        root.querySelectorAll('[data-act]').forEach(b => bind(b, () => actOn(b.dataset.act)));
        root.querySelectorAll('[data-filter]').forEach(c => bind(c, () => {
            root.querySelectorAll('.filter-chip').forEach(x => x.classList.toggle('active', x === c));
            applyFilter(c.dataset.filter);
        }));
        bind(hit, () => { if (mode !== 'live') return; if (!videoReady) tryPlay(); else capture(); });

        setMode('live');
        window.addEventListener('resize', onResize);
        start();
    });
}

// ---------- geometry / warp (pure JS) ----------
const dist = (a, b) => Math.hypot(a.x - b.x, a.y - b.y);

function orderPoints(pts) {
    const bySum = [...pts].sort((a, b) => (a.x + a.y) - (b.x + b.y));
    const byDiff = [...pts].sort((a, b) => (a.y - a.x) - (b.y - b.x));
    return [bySum[0], byDiff[0], bySum[3], byDiff[3]]; // tl, tr, br, bl
}

// Solve 8-param homography mapping the 4 `from` points to the 4 `to` points.
function solveHomography(from, to) {
    const A = [], b = [];
    for (let i = 0; i < 4; i++) {
        const { x, y } = from[i], X = to[i].x, Y = to[i].y;
        A.push([x, y, 1, 0, 0, 0, -X * x, -X * y]); b.push(X);
        A.push([0, 0, 0, x, y, 1, -Y * x, -Y * y]); b.push(Y);
    }
    const h = gauss(A, b);
    return [h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7], 1];
}
function gauss(A, b) {
    const n = 8;
    for (let i = 0; i < n; i++) {
        let max = i;
        for (let r = i + 1; r < n; r++) if (Math.abs(A[r][i]) > Math.abs(A[max][i])) max = r;
        [A[i], A[max]] = [A[max], A[i]]; [b[i], b[max]] = [b[max], b[i]];
        const piv = A[i][i] || 1e-9;
        for (let r = 0; r < n; r++) {
            if (r === i) continue;
            const f = A[r][i] / piv;
            for (let c = i; c < n; c++) A[r][c] -= f * A[i][c];
            b[r] -= f * b[i];
        }
    }
    const x = new Array(n);
    for (let i = 0; i < n; i++) x[i] = b[i] / (A[i][i] || 1e-9);
    return x;
}
function applyH(H, x, y) { const d = H[6] * x + H[7] * y + H[8]; return { x: (H[0] * x + H[1] * y + H[2]) / d, y: (H[3] * x + H[4] * y + H[5]) / d }; }

function warp(srcCanvas, quad) {
    const [tl, tr, br, bl] = quad;
    let W = Math.round(Math.max(dist(br, bl), dist(tr, tl)));
    let H = Math.round(Math.max(dist(tr, br), dist(tl, bl)));
    const longest = Math.max(W, H);
    if (longest > MAX_OUT_EDGE) { const s = MAX_OUT_EDGE / longest; W = Math.round(W * s); H = Math.round(H * s); }
    W = Math.max(1, W); H = Math.max(1, H);
    const dst = [{ x: 0, y: 0 }, { x: W, y: 0 }, { x: W, y: H }, { x: 0, y: H }];
    const Hom = solveHomography(dst, quad); // output(dst) → source(quad)

    const sctx = srcCanvas.getContext('2d');
    const s = sctx.getImageData(0, 0, srcCanvas.width, srcCanvas.height);
    const sd = s.data, sw = s.width, sh = s.height;
    const out = document.createElement('canvas'); out.width = W; out.height = H;
    const octx = out.getContext('2d');
    const o = octx.createImageData(W, H), od = o.data;
    for (let y = 0; y < H; y++) {
        for (let x = 0; x < W; x++) {
            const p = applyH(Hom, x + 0.5, y + 0.5);
            let fx = p.x, fy = p.y;
            if (fx < 0) fx = 0; else if (fx > sw - 1) fx = sw - 1;
            if (fy < 0) fy = 0; else if (fy > sh - 1) fy = sh - 1;
            const x0 = fx | 0, y0 = fy | 0, x1 = Math.min(x0 + 1, sw - 1), y1 = Math.min(y0 + 1, sh - 1);
            const ax = fx - x0, ay = fy - y0;
            const i00 = (y0 * sw + x0) * 4, i10 = (y0 * sw + x1) * 4, i01 = (y1 * sw + x0) * 4, i11 = (y1 * sw + x1) * 4;
            const oi = (y * W + x) * 4;
            for (let c = 0; c < 3; c++) {
                const top = sd[i00 + c] * (1 - ax) + sd[i10 + c] * ax;
                const bot = sd[i01 + c] * (1 - ax) + sd[i11 + c] * ax;
                od[oi + c] = top * (1 - ay) + bot * ay;
            }
            od[oi + 3] = 255;
        }
    }
    octx.putImageData(o, 0, 0);
    return out;
}

// ---------- document region → corner guess (best effort, pure JS) ----------
// Segment the page as the largest connected region that agrees with the centre
// of the frame (Otsu threshold), then take that region's four extreme corners.
// This snaps to the actual document instead of chasing background edges.
function detectCorners(canvas) {
    const W = canvas.width, H = canvas.height;
    const def = [
        { x: W * 0.08, y: H * 0.08 }, { x: W * 0.92, y: H * 0.08 },
        { x: W * 0.92, y: H * 0.92 }, { x: W * 0.08, y: H * 0.92 },
    ];
    try {
        const dw = 320, dh = Math.max(1, Math.round(dw * H / W));
        const tmp = document.createElement('canvas'); tmp.width = dw; tmp.height = dh;
        const tctx = tmp.getContext('2d');
        tctx.drawImage(canvas, 0, 0, dw, dh);
        const data = tctx.getImageData(0, 0, dw, dh).data;
        const N = dw * dh;
        const gray = new Uint8Array(N);
        for (let i = 0, g = 0; g < N; i += 4, g++) gray[g] = (data[i] * 0.299 + data[i + 1] * 0.587 + data[i + 2] * 0.114) | 0;

        const thr = otsuThreshold(gray);
        // Which side of the threshold is the document? Ask the centre of the frame.
        let hi = 0, lo = 0;
        for (let y = (dh * 0.4) | 0; y < dh * 0.6; y++)
            for (let x = (dw * 0.4) | 0; x < dw * 0.6; x++) (gray[y * dw + x] > thr ? hi++ : lo++);
        const fgHigh = hi >= lo;
        const fg = new Uint8Array(N);
        for (let i = 0; i < N; i++) fg[i] = ((gray[i] > thr) === fgHigh) ? 1 : 0;

        // Largest connected component (4-connectivity), tracking corner extremes.
        const visited = new Uint8Array(N);
        const stack = new Int32Array(N);
        let best = null, bestSize = 0;
        for (let start = 0; start < N; start++) {
            if (visited[start] || !fg[start]) continue;
            let sp = 0; stack[sp++] = start; visited[start] = 1;
            let size = 0;
            let tlv = 1e9, brv = -1e9, trv = -1e9, blv = 1e9;
            let tlp, trp, brp, blp;
            while (sp > 0) {
                const idx = stack[--sp];
                const x = idx % dw, y = (idx / dw) | 0;
                size++;
                const s = x + y, d = x - y;
                if (s < tlv) { tlv = s; tlp = { x, y }; }
                if (s > brv) { brv = s; brp = { x, y }; }
                if (d > trv) { trv = d; trp = { x, y }; }
                if (d < blv) { blv = d; blp = { x, y }; }
                if (x > 0) { const j = idx - 1; if (!visited[j] && fg[j]) { visited[j] = 1; stack[sp++] = j; } }
                if (x < dw - 1) { const j = idx + 1; if (!visited[j] && fg[j]) { visited[j] = 1; stack[sp++] = j; } }
                if (y > 0) { const j = idx - dw; if (!visited[j] && fg[j]) { visited[j] = 1; stack[sp++] = j; } }
                if (y < dh - 1) { const j = idx + dw; if (!visited[j] && fg[j]) { visited[j] = 1; stack[sp++] = j; } }
            }
            if (size > bestSize) { bestSize = size; best = [tlp, trp, brp, blp]; }
        }

        // Reject if the region is too small (noise). A very large region (page fills
        // the frame) is fine — corners land near the edges, which is correct.
        if (!best || bestSize < 0.10 * N) return def;
        const sx = W / dw, sy = H / dh;
        return best.map(p => ({ x: p.x * sx, y: p.y * sy }));
    } catch (_) {
        return def;
    }
}

function otsuThreshold(gray) {
    const hist = new Uint32Array(256);
    for (let i = 0; i < gray.length; i++) hist[gray[i]]++;
    const n = gray.length;
    let sum = 0; for (let i = 0; i < 256; i++) sum += i * hist[i];
    let sumB = 0, wB = 0, max = 0, thr = 127;
    for (let i = 0; i < 256; i++) {
        wB += hist[i]; if (!wB) continue;
        const wF = n - wB; if (!wF) break;
        sumB += i * hist[i];
        const mB = sumB / wB, mF = (sum - sumB) / wF;
        const v = wB * wF * (mB - mF) * (mB - mF);
        if (v > max) { max = v; thr = i; }
    }
    return thr;
}

// ---------- enhancement filters ----------
function autoContrast(src, dst) {
    const hist = new Uint32Array(256);
    for (let i = 0; i < src.length; i += 4) { const l = (src[i] * 0.299 + src[i + 1] * 0.587 + src[i + 2] * 0.114) | 0; hist[l]++; }
    const total = src.length / 4;
    let lo = 0, hi = 255, acc = 0;
    for (let i = 0; i < 256; i++) { acc += hist[i]; if (acc > total * 0.02) { lo = i; break; } }
    acc = 0;
    for (let i = 255; i >= 0; i--) { acc += hist[i]; if (acc > total * 0.02) { hi = i; break; } }
    const range = Math.max(1, hi - lo);
    for (let i = 0; i < src.length; i += 4) {
        for (let c = 0; c < 3; c++) { let v = (src[i + c] - lo) * 255 / range; dst[i + c] = v < 0 ? 0 : v > 255 ? 255 : v; }
        dst[i + 3] = 255;
    }
}
function grayscaleOtsu(src, dst) {
    const n = src.length / 4;
    const gray = new Uint8ClampedArray(n), hist = new Uint32Array(256);
    for (let i = 0, g = 0; i < src.length; i += 4, g++) { const v = (src[i] * 0.299 + src[i + 1] * 0.587 + src[i + 2] * 0.114) | 0; gray[g] = v; hist[v]++; }
    let sum = 0; for (let i = 0; i < 256; i++) sum += i * hist[i];
    let sumB = 0, wB = 0, maxVar = 0, thr = 127;
    for (let i = 0; i < 256; i++) {
        wB += hist[i]; if (!wB) continue;
        const wF = n - wB; if (!wF) break;
        sumB += i * hist[i];
        const mB = sumB / wB, mF = (sum - sumB) / wF;
        const between = wB * wF * (mB - mF) * (mB - mF);
        if (between > maxVar) { maxVar = between; thr = i; }
    }
    for (let g = 0, i = 0; g < n; g++, i += 4) { const v = gray[g] > thr ? 255 : 0; dst[i] = dst[i + 1] = dst[i + 2] = v; dst[i + 3] = 255; }
}
