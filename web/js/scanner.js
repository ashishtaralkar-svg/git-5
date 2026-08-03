// In-browser document scanner for mobile web.
// Opens a full-screen camera, lets the user capture pages, apply
// Original / Color / B&W enhancement filters, and scan multiple pages.
// Resolves with an array of JPEG Blobs (the scanned pages).

const MAX_EDGE = 2000; // downscale captured frames for performance/storage

export function openScanner() {
    return new Promise((resolve, reject) => {
        const pages = [];
        let stream = null;
        let track = null;
        let torchOn = false;

        const root = document.createElement('div');
        root.className = 'scanner';
        root.innerHTML = `
            <div class="scan-topbar">
                <button class="icon-btn" data-act="close" aria-label="Close">✕</button>
                <span class="title">Scan Document</span>
                <span class="spacer"></span>
                <button class="scan-side-btn" data-act="torch" aria-label="Flash">⚡</button>
            </div>
            <video autoplay playsinline muted></video>
            <canvas class="preview hidden"></canvas>
            <div class="scan-overlay live"><div class="scan-frame"></div></div>

            <div class="filter-bar review hidden">
                <button class="filter-chip" data-filter="original">Original</button>
                <button class="filter-chip active" data-filter="color">Color</button>
                <button class="filter-chip" data-filter="bw">B&amp;W</button>
            </div>

            <div class="scan-controls live">
                <button class="scan-side-btn" data-act="done" title="Done">✓<span class="count"></span></button>
                <button class="shutter" data-act="capture" aria-label="Capture"></button>
                <span style="width:52px"></span>
            </div>
            <div class="scan-controls review hidden">
                <button class="scan-side-btn" data-act="retake" title="Retake">↺</button>
                <button class="shutter" data-act="keep" aria-label="Keep" style="background:#1a7f37;border-color:#fff"></button>
                <span style="width:52px"></span>
            </div>
        `;
        document.getElementById('modal-root').appendChild(root);

        const video = root.querySelector('video');
        const canvas = root.querySelector('canvas.preview');
        const ctx = canvas.getContext('2d', { willReadFrequently: true });
        let originalImageData = null;
        let currentFilter = 'color';

        const show = (sel, on) => root.querySelectorAll(sel).forEach(e => e.classList.toggle('hidden', !on));
        const updateCount = () => {
            const c = root.querySelector('.count');
            c.textContent = pages.length ? ` ${pages.length}` : '';
        };

        function cleanup(result) {
            if (stream) stream.getTracks().forEach(t => t.stop());
            root.remove();
            resolve(result);
        }

        async function start() {
            try {
                stream = await navigator.mediaDevices.getUserMedia({
                    video: { facingMode: { ideal: 'environment' }, width: { ideal: 1920 }, height: { ideal: 1080 } },
                    audio: false,
                });
                video.srcObject = stream;
                track = stream.getVideoTracks()[0];
            } catch (err) {
                cleanupErr(err);
            }
        }

        function cleanupErr(err) {
            root.remove();
            reject(err);
        }

        // ----- capture -----
        function capture() {
            const vw = video.videoWidth, vh = video.videoHeight;
            if (!vw || !vh) return;
            let w = vw, h = vh;
            const longest = Math.max(w, h);
            if (longest > MAX_EDGE) {
                const s = MAX_EDGE / longest;
                w = Math.round(w * s); h = Math.round(h * s);
            }
            canvas.width = w; canvas.height = h;
            ctx.drawImage(video, 0, 0, w, h);
            originalImageData = ctx.getImageData(0, 0, w, h);
            currentFilter = 'color';
            applyFilter('color');
            // switch to review mode
            video.classList.add('hidden');
            canvas.classList.remove('hidden');
            show('.scan-overlay.live', false);
            show('.scan-controls.live', false);
            show('.filter-bar.review', true);
            show('.scan-controls.review', true);
            root.querySelectorAll('.filter-chip').forEach(c =>
                c.classList.toggle('active', c.dataset.filter === 'color'));
        }

        function backToLive() {
            canvas.classList.add('hidden');
            video.classList.remove('hidden');
            show('.scan-overlay.live', true);
            show('.scan-controls.live', true);
            show('.filter-bar.review', false);
            show('.scan-controls.review', false);
        }

        function keep() {
            canvas.toBlob((blob) => {
                if (blob) { pages.push(blob); updateCount(); }
                backToLive();
            }, 'image/jpeg', 0.85);
        }

        // ----- filters -----
        function applyFilter(mode) {
            if (!originalImageData) return;
            const src = originalImageData.data;
            const out = ctx.createImageData(canvas.width, canvas.height);
            const dst = out.data;
            if (mode === 'original') {
                dst.set(src);
            } else if (mode === 'color') {
                // Auto contrast stretch + mild brightness for a clean scanned look.
                autoContrast(src, dst);
            } else if (mode === 'bw') {
                grayscaleOtsu(src, dst);
            }
            ctx.putImageData(out, 0, 0);
        }

        async function toggleTorch() {
            if (!track) return;
            try {
                const caps = track.getCapabilities ? track.getCapabilities() : {};
                if (!caps.torch) { flash('Flash not supported on this device'); return; }
                torchOn = !torchOn;
                await track.applyConstraints({ advanced: [{ torch: torchOn }] });
                root.querySelector('[data-act=torch]').classList.toggle('active', torchOn);
            } catch (_) { /* ignore */ }
        }

        function flash(msg) {
            const t = document.createElement('div');
            t.className = 'toast'; t.textContent = msg;
            root.appendChild(t);
            setTimeout(() => t.remove(), 1800);
        }

        // ----- events -----
        root.addEventListener('click', (e) => {
            const act = e.target.closest('[data-act]')?.dataset.act;
            const filter = e.target.closest('[data-filter]')?.dataset.filter;
            if (filter) {
                currentFilter = filter;
                root.querySelectorAll('.filter-chip').forEach(c => c.classList.toggle('active', c === e.target));
                applyFilter(filter);
                return;
            }
            switch (act) {
                case 'capture': capture(); break;
                case 'keep': keep(); break;
                case 'retake': backToLive(); break;
                case 'torch': toggleTorch(); break;
                case 'done': cleanup(pages); break;
                case 'close': cleanup(pages); break;
            }
        });

        updateCount();
        start();
    });
}

// ---------- image processing helpers ----------

function autoContrast(src, dst) {
    // Find 2nd/98th percentile luminance and stretch.
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
    // Otsu threshold
    let sum = 0;
    for (let i = 0; i < 256; i++) sum += i * hist[i];
    let sumB = 0, wB = 0, maxVar = 0, threshold = 127;
    for (let i = 0; i < 256; i++) {
        wB += hist[i];
        if (wB === 0) continue;
        const wF = n - wB;
        if (wF === 0) break;
        sumB += i * hist[i];
        const mB = sumB / wB;
        const mF = (sum - sumB) / wF;
        const between = wB * wF * (mB - mF) * (mB - mF);
        if (between > maxVar) { maxVar = between; threshold = i; }
    }
    for (let g = 0, i = 0; g < n; g++, i += 4) {
        const v = gray[g] > threshold ? 255 : 0;
        dst[i] = dst[i + 1] = dst[i + 2] = v;
        dst[i + 3] = 255;
    }
}
