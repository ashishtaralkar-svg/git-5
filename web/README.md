# HDFC DocUpload — Web version

A browser-based counterpart to the Android app. It mirrors the same flow —
Login → Search Application → Upload Documents → Preview → Upload → Uploaded list —
and adapts the **Scan Document** feature to the device:

- **On a mobile browser:** tapping *Scan Document* opens the device **camera**
  full-screen via the **Dynamsoft Document Scanner** widget — it tracks the
  page edges live, **auto-captures** when the document is held steady, and
  walks through a correction screen (draggable corners, perspective deskew)
  and a result screen (Original / Grayscale / B&W / Sepia). It stays open for
  **multi-page** scanning, accumulating a page each time one is accepted,
  until the user closes it. If the scanner can't load (e.g. the Dynamsoft CDN
  is unreachable), the app falls back to the native camera capture input.
- **On a laptop/desktop browser:** tapping *Scan Document* shows a **popup**
  explaining the scanner is available on mobile devices only. Everything else
  (upload files, preview, upload, uploaded list) still works.

## Features

- Login (`hdfc` / `123`) with validation and the exact `Invalid Username or Password.` message.
- Search Application with alphanumeric-only enforcement and blank validation.
- Upload from device: images **and** PDF, multiple selection, thumbnails, remove.
- Scan (mobile): camera, capture, filters, multi-page, torch (where supported),
  with graceful fallback to the native camera input if the camera API is blocked.
- Upload with animated progress → success screen (*View Uploaded* / *Upload More*).
- Uploaded list: thumbnail, name, size, date/time, preview, delete.
- Offline aware: documents persist in **IndexedDB**; upload is blocked with a
  friendly message when offline (no backend — the demo simulates upload).
- Material-inspired UI, rounded corners, animations, **light & dark** mode.
- No build step — pure HTML/CSS/ES modules. The one external dependency is the
  Dynamsoft Document Scanner widget, loaded from its CDN on demand when the
  scanner is opened (see `js/scanner.js`).

## Why it's hosted (not opened as a file)

Browser camera access (`getUserMedia`) requires a **secure context** (HTTPS or
`localhost`). Opening `index.html` directly from disk (`file://`) disables the
camera. The GitHub Pages deployment serves the app over HTTPS so the mobile
scanner works.

## Run locally

```bash
cd web
python3 -m http.server 8000
# open http://localhost:8000  (localhost counts as secure, so the camera works)
```

To test the mobile experience on your phone against a laptop dev server, use the
deployed Pages URL (HTTPS) — phones won't reach `localhost` on your laptop and
need HTTPS for the camera anyway.

## Deployment

`.github/workflows/pages.yml` publishes this `web/` folder to GitHub Pages on
every push. The live URL is:

```
https://ashishtaralkar-svg.github.io/git-5/
```
