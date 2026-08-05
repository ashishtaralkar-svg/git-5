// In-browser document scanner, powered by Dynamsoft Document Scanner (MDS).
// Gives live camera capture with automatic edge detection + auto-capture, a
// correction screen (draggable corners, perspective deskew) and a result
// screen (Original / Grayscale / B&W / Sepia filters) out of the box.
// Multi-page: the scanner stays open after each capture (continuous scanning)
// and we accumulate every accepted page until the user closes it.

// The UMD build (attaches `window.Dynamsoft`), not the ESM build — the ESM
// build has a bare `import ... from "dynamsoft-capture-vision-bundle"` that
// only resolves under a bundler/import-map, and fails in a plain browser
// `import()` with "Failed to resolve module specifier". The UMD build is
// fully self-contained. Two independent CDN mirrors: if one is unreachable
// (blocked network, transient CDN issue) the other is tried before giving up.
const DDS_URLS = [
    'https://cdn.jsdelivr.net/npm/dynamsoft-document-scanner@1.5.0/dist/dds.bundle.js',
    'https://unpkg.com/dynamsoft-document-scanner@1.5.0/dist/dds.bundle.js',
];

// Dynamsoft license key (client-side keys are expected — usage is enforced by
// Dynamsoft's license server, not by keeping this secret).
const DYNAMSOFT_LICENSE = 'DLS2eyJoYW5kc2hha2VDb2RlIjoiMTA2MDc3MTAxLU1UQTJNRGMzTVRBeExYZGxZaTFVY21saGJGQnliMm8iLCJtYWluU2VydmVyVVJMIjoiaHR0cHM6Ly9tZGxzLmR5bmFtc29mdG9ubGluZS5jb20vIiwib3JnYW5pemF0aW9uSUQiOiIxMDYwNzcxMDEiLCJzdGFuZGJ5U2VydmVyVVJMIjoiaHR0cHM6Ly9zZGxzLmR5bmFtc29mdG9ubGluZS5jb20vIiwiY2hlY2tDb2RlIjoxNDI2NTgzNDU1fQ==';

function loadScript(url) {
    return new Promise((resolve, reject) => {
        const s = document.createElement('script');
        s.src = url;
        s.onload = () => resolve();
        s.onerror = () => reject(new Error(`Failed to load ${url}`));
        document.head.appendChild(s);
    });
}

let DocumentScannerCtor = null;
async function loadDocumentScanner() {
    if (DocumentScannerCtor) return DocumentScannerCtor;
    if (window.Dynamsoft?.DocumentScanner) return (DocumentScannerCtor = window.Dynamsoft.DocumentScanner);
    let lastErr;
    for (const url of DDS_URLS) {
        try {
            await loadScript(url);
            if (window.Dynamsoft?.DocumentScanner) return (DocumentScannerCtor = window.Dynamsoft.DocumentScanner);
            lastErr = new Error(`Loaded ${url} but window.Dynamsoft.DocumentScanner was not found`);
        } catch (err) {
            lastErr = err;
        }
    }
    throw lastErr;
}

export async function openScanner() {
    const host = document.createElement('div');
    host.id = 'scanner-host';
    host.style.cssText = 'position:fixed;inset:0;z-index:90;';
    host.innerHTML = '<div class="scanner-loading">Loading scanner…</div>';
    document.getElementById('modal-root').appendChild(host);

    const pages = [];
    let scanner = null;
    try {
        const DocumentScanner = await loadDocumentScanner();
        host.innerHTML = '';
        scanner = new DocumentScanner({
            license: DYNAMSOFT_LICENSE,
            container: host,
            enableContinuousScanning: true,
            scannerViewConfig: {
                enableAutoCropMode: true,
                enableSmartCaptureMode: true,
            },
            onDocumentScanned: async (result) => {
                if (!result?.correctedImageResult) return;
                const blob = await result.correctedImageResult.toBlob('image/jpeg', 0.9);
                if (blob) pages.push(blob);
            },
        });
        await scanner.launch();
        return pages;
    } finally {
        try { scanner && scanner.dispose(); } catch (_) {}
        host.remove();
    }
}
