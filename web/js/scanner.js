// In-browser document scanner, powered by Dynamsoft Document Scanner (MDS).
// Gives live camera capture with automatic edge detection + auto-capture, a
// correction screen (draggable corners, perspective deskew) and a result
// screen (Original / Grayscale / B&W / Sepia filters) out of the box.
// Multi-page: the scanner stays open after each capture (continuous scanning)
// and we accumulate every accepted page until the user closes it.

const DDS_URL = 'https://cdn.jsdelivr.net/npm/dynamsoft-document-scanner@1.5.0/dist/dds.bundle.esm.js';

// Dynamsoft license key (client-side keys are expected — usage is enforced by
// Dynamsoft's license server, not by keeping this secret).
const DYNAMSOFT_LICENSE = 'DLS2eyJoYW5kc2hha2VDb2RlIjoiMTA2MDc3MTAxLU1UQTJNRGMzTVRBeExYZGxZaTFVY21saGJGQnliMm8iLCJtYWluU2VydmVyVVJMIjoiaHR0cHM6Ly9tZGxzLmR5bmFtc29mdG9ubGluZS5jb20vIiwib3JnYW5pemF0aW9uSUQiOiIxMDYwNzcxMDEiLCJzdGFuZGJ5U2VydmVyVVJMIjoiaHR0cHM6Ly9zZGxzLmR5bmFtc29mdG9ubGluZS5jb20vIiwiY2hlY2tDb2RlIjoxNDI2NTgzNDU1fQ==';

let DocumentScannerCtor = null;
async function loadDocumentScanner() {
    if (!DocumentScannerCtor) {
        const mod = await import(DDS_URL);
        DocumentScannerCtor = mod.DocumentScanner;
    }
    return DocumentScannerCtor;
}

export async function openScanner() {
    const DocumentScanner = await loadDocumentScanner();

    const host = document.createElement('div');
    host.id = 'scanner-host';
    host.style.cssText = 'position:fixed;inset:0;z-index:90;';
    document.getElementById('modal-root').appendChild(host);

    const pages = [];
    let scanner = null;
    try {
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
