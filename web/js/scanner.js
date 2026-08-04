// In-browser document scanner for mobile web with automatic document
// detection, auto-crop and perspective correction (OpenCV.js), mirroring the
// ML Kit experience of the Android app — designed to stay responsive:
//   - the live preview is the native <video> element (GPU-composited); JS never
//     draws it frame-by-frame, so the page can't jank/hang on the preview.
//   - document detection runs on a throttled timer (~4x/sec) on a tiny 480px
//     snapshot, guarded so a slow or failing detection never locks the UI.
//   - the camera + manual shutter work immediately, even before OpenCV loads;
//     if OpenCV never loads, it simply captures the full frame.
//   - auto-capture when the document is held steady, perspective warp + crop,
//     Original / Color / B&W enhancement, multi-page, torch, retake.

const OPENCV_URL = 'https://docs.opencv.org/4.x/opencv.js';
const MAX_OUT_EDGE = 1600;
const DETECT_INTERVAL_MS = 220;
const DETECT_WIDTH = 480;

let cvPromise = null;
function loadOpenCV() {
    if (cvPromise) return cvPromise;
    cvPromise = new Promise((resolve) => {
        if (window.cv && window.cv.Mat) return resolve(window.cv);
        const script = document.createElement('script');
        script.src = OPENCV_URL;
        script.async = true;
        let settled = false;
        const done = (v) => { if (!settled) { settled = true; resolve(v); } };
        script.onload = () => {
            const cv = window.cv;
            if (!cv) return done(null);
            if (cv.Mat) return done(cv);
            if (typeof cv.then === 'function') { cv.then((c) => { window.cv = c; done(c); }); return; }
            cv.onRuntimeInitialized = () => done(window.cv);
        };
        script.onerror = () => done(null);
        document.head.appendChild(script);
        setTimeout(() => done(window.cv && window.cv.Mat ? window.cv : null), 8000);
    });
    return cvPromise;
}

export function openScanner() {
    return new Promise((resolve, reject) => {
        const pages = [];
        let stream = null, track = null, torchOn = false;
        let cv = null, detecting = false;
        let detectTimer = null, rafId = null;
        let latestQuad = null;          // video-pixel coords
        let stableCount = 0, lastCentroid = null;
        let autoMode = true;
        let mode = 'live';
        let originalImageData = null;
        let videoReady = false, cvTried = false;

        const root = document.createElement('div');
        root.className = 'scanner';
        root.innerHTML = `
            <div class="scan-topbar">
                <button class="icon-btn" data-act="close" aria-label="Close">✕</button>
                <span class="title" id="scan-status">Starting camera…</span>
                <span class="spacer"></span>
                <button class="scan-side-btn" data-act="torch" aria-label="Flash">⚡</button>
            </div>
            <video autoplay playsinline muted></video>
            <canvas class="overlay"></canvas>
            <canvas class="preview hidden"></canvas>
            <button class="tap-start hidden" data-act="start">▶<span>Tap to start camera</span></button>

            <div class="filter-bar review hidden">
                <button class="filter-chip" data-filter="original">Original</button>
                <button class="filter-chip active" data-filter="color">Color</button>
                <button class="filter-chip" data-filter="bw">B&amp;W</button>
            </div>

            <div class="scan-controls live">
                <button class="scan-side-btn" data-act="auto" title="Auto capture">A</button>
                <button class="shutter" data-act="capture" aria-label="Capture"></button>
                <button class="scan-side-btn" data-act="done" title="Done">✓<span class="count"></span></button>
            </div>
            <div class="scan-controls review hidden">
                <button class="scan-side-btn" data-act="retake" title="Retake">↺</button>
                <button class="shutter" data-act="keep" aria-label="Keep" style="background:#1a7f37;border-color:#fff"></button>
                <span style="width:52px"></span>
            </div>`;
        document.getElementById('modal-root').appendChild(root);

        const video = root.querySelector('video');
        const overlay = root.querySelector('canvas.overlay');
        const octx = overlay.getContext('2d');
        const review = root.querySelector('canvas.preview');
        const rctx = review.getContext('2d', { willReadFrequently: true });
        const det = document.createElement('canvas');
        const dctx = det.getContext('2d', { willReadFrequently: true });
        const full = document.createElement('canvas');
        const fctx = full.getContext('2d');
        const statusEl = root.querySelector('#scan-status');

        const setStatus = (t) => { statusEl.textContent = t; };
        const show = (sel, on) => root.querySelectorAll(sel).forEach(e => e.classList.toggle('hidden', !on));
        const updateCount = () => { root.querySelector('.count').textContent = pages.length ? ` ${pages.length}` : ''; };
        const setAutoBtn = () => root.querySelector('[data-act=auto]').classList.toggle('active', autoMode);

        function sizeOverlay() {
            const dpr = Math.min(window.devicePixelRatio || 1, 2);
            overlay.width = Math.round(overlay.clientWidth * dpr);
            overlay.height = Math.round(overlay.clientHeight * dpr);
            octx.setTransform(dpr, 0, 0, dpr, 0, 0);
        }

        // Cover-fit mapping: video-pixel coords → overlay CSS-px coords.
        function cover() {
            const ew = overlay.clientWidth, eh = overlay.clientHeight;
            const vw = video.videoWidth, vh = video.videoHeight;
            if (!vw || !vh) return null;
            const scale = Math.max(ew / vw, eh / vh);
            return { scale, dx: (ew - vw * scale) / 2, dy: (eh - vh * scale) / 2, vw, vh, ew, eh };
        }

        function cleanup(result) {
            if (detectTimer) clearInterval(detectTimer);
            if (rafId) cancelAnimationFrame(rafId);
            if (stream) stream.getTracks().forEach(t => t.stop());
            window.removeEventListener('resize', sizeOverlay);
            root.remove();
            resolve(result);
        }

        function refreshStatus() {
            if (mode !== 'live') return;
            if (!videoReady) { setStatus('Starting camera…'); return; }
            if (!cvTried) { setStatus('Tap to capture · loading auto-detect…'); return; }
            if (!cv) { setStatus('Tap anywhere to capture'); return; }
            setStatus(autoMode ? 'Point at a document' : 'Tap to capture');
        }

        const tapStartBtn = () => root.querySelector('.tap-start');
        function tryPlay() {
            video.muted = true; video.playsInline = true;
            const p = video.play();
            if (p && p.catch) p.catch(() => {});
        }
        function showTapStart(on) { tapStartBtn().classList.toggle('hidden', !on); }

        async function start() {
            setAutoBtn();
            updateCount();
            setStatus('Starting camera…');

            // Load OpenCV in the background — never blocks the camera or capture.
            loadOpenCV().then((c) => { cv = c; cvTried = true; refreshStatus(); });

            // Mark ready as soon as the stream produces frames.
            const onPlaying = () => { videoReady = true; showTapStart(false); sizeOverlay(); refreshStatus(); };
            video.addEventListener('loadedmetadata', () => { sizeOverlay(); tryPlay(); });
            video.addEventListener('playing', onPlaying);
            video.addEventListener('canplay', () => { tryPlay(); if (video.videoWidth) onPlaying(); });

            try {
                stream = await navigator.mediaDevices.getUserMedia({
                    video: { facingMode: { ideal: 'environment' } },
                    audio: false,
                });
                video.srcObject = stream;
                track = stream.getVideoTracks()[0];
                tryPlay(); // awaiting getUserMedia consumed the tap's user-activation…
                rafId = requestAnimationFrame(drawOverlay);
                detectTimer = setInterval(tick, DETECT_INTERVAL_MS);

                // …so if autoplay is blocked and no frames arrive shortly, reveal an
                // explicit "Tap to start" button (a fresh user gesture reliably plays).
                setTimeout(() => {
                    if (!videoReady && mode === 'live') {
                        showTapStart(true);
                        setStatus('Tap “Start camera” below');
                    }
                }, 1200);
            } catch (err) {
                root.remove();
                reject(err);
            }
        }

        // rAF only clears + redraws the (cheap) outline; no video pixels touched.
        function drawOverlay() {
            rafId = requestAnimationFrame(drawOverlay);
            if (mode !== 'live') return;
            octx.clearRect(0, 0, overlay.clientWidth, overlay.clientHeight);
            const m = cover();
            if (!m || !latestQuad) return;
            const pts = latestQuad.map(p => ({ x: m.dx + p.x * m.scale, y: m.dy + p.y * m.scale }));
            octx.lineWidth = 3;
            octx.strokeStyle = '#4ade80';
            octx.fillStyle = 'rgba(74,222,128,0.15)';
            octx.beginPath();
            pts.forEach((p, i) => i ? octx.lineTo(p.x, p.y) : octx.moveTo(p.x, p.y));
            octx.closePath();
            octx.fill(); octx.stroke();
        }

        // Throttled, guarded detection. Runs at most once per interval and never
        // overlaps itself, so a slow frame can't pile up and hang the page.
        function tick() {
            if (mode !== 'live' || !cv || detecting) return;
            const vw = video.videoWidth, vh = video.videoHeight;
            if (!vw || !vh) return;
            detecting = true;
            try {
                const dw = DETECT_WIDTH, dh = Math.max(1, Math.round(dw * vh / vw));
                det.width = dw; det.height = dh;
                dctx.drawImage(video, 0, 0, dw, dh);
                const quad = detectDocument(cv, det);
                if (quad) {
                    const sx = vw / dw, sy = vh / dh;
                    const q = quad.map(p => ({ x: p.x * sx, y: p.y * sy }));
                    const c = centroid(q);
                    if (lastCentroid && dist(c, lastCentroid) < vw * 0.035) stableCount++;
                    else stableCount = 0;
                    lastCentroid = c; latestQuad = q;
                    if (autoMode && stableCount >= 5) { stableCount = 0; capture(); }
                    else setStatus(autoMode ? 'Hold steady…' : 'Detected — tap shutter');
                } else {
                    latestQuad = null; stableCount = 0; lastCentroid = null;
                    setStatus('Searching for document…');
                }
            } catch (_) {
                cv = null; // disable detection on error; manual capture still works
                setStatus('Tap the shutter to capture');
            } finally {
                detecting = false;
            }
        }

        function capture() {
          try {
            const vw = video.videoWidth, vh = video.videoHeight;
            if (!vw || !vh) { setStatus('Camera not ready yet — one moment…'); return; }
            full.width = vw; full.height = vh;
            fctx.drawImage(video, 0, 0, vw, vh);

            let outCanvas = full;
            if (cv && latestQuad) {
                try { outCanvas = warpPerspectiveCanvas(cv, full, latestQuad); } catch (_) { outCanvas = full; }
            }
            let w = outCanvas.width, h = outCanvas.height;
            const longest = Math.max(w, h);
            if (longest > MAX_OUT_EDGE) { const s = MAX_OUT_EDGE / longest; w = Math.round(w * s); h = Math.round(h * s); }
            review.width = w; review.height = h;
            rctx.drawImage(outCanvas, 0, 0, w, h);
            originalImageData = rctx.getImageData(0, 0, w, h);

            mode = 'review';
            octx.clearRect(0, 0, overlay.clientWidth, overlay.clientHeight);
            applyFilter('color');
            review.classList.remove('hidden');
            overlay.classList.add('hidden');
            video.classList.add('hidden');
            show('.scan-controls.live', false);
            show('.filter-bar.review', true);
            show('.scan-controls.review', true);
            root.querySelectorAll('.filter-chip').forEach(c => c.classList.toggle('active', c.dataset.filter === 'color'));
            setStatus(cv && latestQuad ? 'Cropped & enhanced' : 'Captured');
          } catch (err) {
            setStatus('Capture failed: ' + ((err && err.message) || err));
          }
        }

        function backToLive() {
            mode = 'live';
            latestQuad = null; stableCount = 0; lastCentroid = null;
            review.classList.add('hidden');
            overlay.classList.remove('hidden');
            video.classList.remove('hidden');
            show('.scan-controls.live', true);
            show('.filter-bar.review', false);
            show('.scan-controls.review', false);
            refreshStatus();
        }

        function keep() {
            review.toBlob((blob) => {
                if (blob) { pages.push(blob); updateCount(); }
                backToLive();
            }, 'image/jpeg', 0.85);
        }

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
            } catch (_) { /* ignore */ }
        }

        root.addEventListener('click', (e) => {
            const act = e.target.closest('[data-act]')?.dataset.act;
            const chip = e.target.closest('[data-filter]');
            if (chip) {
                root.querySelectorAll('.filter-chip').forEach(c => c.classList.toggle('active', c === chip));
                applyFilter(chip.dataset.filter);
                return;
            }
            // Before the stream is playing, any tap forces playback (user gesture).
            if (mode === 'live' && !videoReady) { tryPlay(); return; }
            // Once live, a tap anywhere except the buttons captures (forgiving target).
            const onControl = e.target.closest('.scan-controls, .scan-topbar, .filter-bar, .tap-start');
            if (mode === 'live' && !act && !chip && !onControl) { capture(); return; }
            switch (act) {
                case 'start': tryPlay(); break;
                case 'capture': if (mode === 'live') capture(); break;
                case 'keep': keep(); break;
                case 'retake': backToLive(); break;
                case 'auto': autoMode = !autoMode; setAutoBtn(); break;
                case 'torch': toggleTorch(); break;
                case 'done':
                case 'close': cleanup(pages); break;
            }
        });

        window.addEventListener('resize', sizeOverlay);
        start();
    });
}

// ---------- OpenCV document detection & warp ----------

function detectDocument(cv, srcCanvas) {
    const src = cv.imread(srcCanvas);
    const gray = new cv.Mat(), edges = new cv.Mat();
    const contours = new cv.MatVector(), hier = new cv.Mat();
    let best = null, bestArea = 0;
    try {
        cv.cvtColor(src, gray, cv.COLOR_RGBA2GRAY);
        cv.GaussianBlur(gray, gray, new cv.Size(5, 5), 0);
        cv.Canny(gray, edges, 75, 200);
        const kernel = cv.Mat.ones(3, 3, cv.CV_8U);
        cv.dilate(edges, edges, kernel);
        kernel.delete();
        cv.findContours(edges, contours, hier, cv.RETR_LIST, cv.CHAIN_APPROX_SIMPLE);
        const imgArea = srcCanvas.width * srcCanvas.height;
        for (let i = 0; i < contours.size(); i++) {
            const cnt = contours.get(i);
            const area = cv.contourArea(cnt);
            if (area > 0.2 * imgArea && area > bestArea) {
                const peri = cv.arcLength(cnt, true);
                const approx = new cv.Mat();
                cv.approxPolyDP(cnt, approx, 0.02 * peri, true);
                if (approx.rows === 4 && cv.isContourConvex(approx)) {
                    bestArea = area;
                    best = [];
                    for (let k = 0; k < 4; k++) best.push({ x: approx.data32S[k * 2], y: approx.data32S[k * 2 + 1] });
                }
                approx.delete();
            }
            cnt.delete();
        }
    } finally {
        src.delete(); gray.delete(); edges.delete(); contours.delete(); hier.delete();
    }
    return best;
}

function orderPoints(pts) {
    const bySum = [...pts].sort((a, b) => (a.x + a.y) - (b.x + b.y));
    const byDiff = [...pts].sort((a, b) => (a.y - a.x) - (b.y - b.x));
    return [bySum[0], byDiff[0], bySum[3], byDiff[3]]; // tl, tr, br, bl
}

function warpPerspectiveCanvas(cv, srcCanvas, quad) {
    const [tl, tr, br, bl] = orderPoints(quad);
    const wA = Math.hypot(br.x - bl.x, br.y - bl.y), wB = Math.hypot(tr.x - tl.x, tr.y - tl.y);
    const hA = Math.hypot(tr.x - br.x, tr.y - br.y), hB = Math.hypot(tl.x - bl.x, tl.y - bl.y);
    const W = Math.max(1, Math.round(Math.max(wA, wB)));
    const H = Math.max(1, Math.round(Math.max(hA, hB)));
    const src = cv.imread(srcCanvas);
    const dst = new cv.Mat();
    const srcTri = cv.matFromArray(4, 1, cv.CV_32FC2, [tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y]);
    const dstTri = cv.matFromArray(4, 1, cv.CV_32FC2, [0, 0, W, 0, W, H, 0, H]);
    const M = cv.getPerspectiveTransform(srcTri, dstTri);
    cv.warpPerspective(src, dst, M, new cv.Size(W, H), cv.INTER_LINEAR, cv.BORDER_CONSTANT, new cv.Scalar());
    const out = document.createElement('canvas');
    out.width = W; out.height = H;
    cv.imshow(out, dst);
    src.delete(); dst.delete(); M.delete(); srcTri.delete(); dstTri.delete();
    return out;
}

const centroid = (q) => ({ x: (q[0].x + q[1].x + q[2].x + q[3].x) / 4, y: (q[0].y + q[1].y + q[2].y + q[3].y) / 4 });
const dist = (a, b) => Math.hypot(a.x - b.x, a.y - b.y);

// ---------- enhancement filters ----------

function autoContrast(src, dst) {
    const hist = new Uint32Array(256);
    for (let i = 0; i < src.length; i += 4) {
        const lum = (src[i] * 0.299 + src[i + 1] * 0.587 + src[i + 2] * 0.114) | 0;
        hist[lum]++;
    }
    const total = src.length / 4;
    let lo = 0, hi = 255, acc = 0;
    for (let i = 0; i < 256; i++) { acc += hist[i]; if (acc > total * 0.02) { lo = i; break; } }
    acc = 0;
    for (let i = 255; i >= 0; i--) { acc += hist[i]; if (acc > total * 0.02) { hi = i; break; } }
    const range = Math.max(1, hi - lo);
    for (let i = 0; i < src.length; i += 4) {
        for (let c = 0; c < 3; c++) {
            let v = (src[i + c] - lo) * 255 / range;
            dst[i + c] = v < 0 ? 0 : v > 255 ? 255 : v;
        }
        dst[i + 3] = 255;
    }
}

function grayscaleOtsu(src, dst) {
    const n = src.length / 4;
    const gray = new Uint8ClampedArray(n);
    const hist = new Uint32Array(256);
    for (let i = 0, g = 0; i < src.length; i += 4, g++) {
        const v = (src[i] * 0.299 + src[i + 1] * 0.587 + src[i + 2] * 0.114) | 0;
        gray[g] = v; hist[v]++;
    }
    let sum = 0;
    for (let i = 0; i < 256; i++) sum += i * hist[i];
    let sumB = 0, wB = 0, maxVar = 0, threshold = 127;
    for (let i = 0; i < 256; i++) {
        wB += hist[i];
        if (wB === 0) continue;
        const wF = n - wB;
        if (wF === 0) break;
        sumB += i * hist[i];
        const mB = sumB / wB, mF = (sum - sumB) / wF;
        const between = wB * wF * (mB - mF) * (mB - mF);
        if (between > maxVar) { maxVar = between; threshold = i; }
    }
    for (let g = 0, i = 0; g < n; g++, i += 4) {
        const v = gray[g] > threshold ? 255 : 0;
        dst[i] = dst[i + 1] = dst[i + 2] = v; dst[i + 3] = 255;
    }
}
