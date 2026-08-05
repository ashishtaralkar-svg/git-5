# DocUpload — iOS

A native iOS counterpart to the web and Android apps, mirroring the same flow —
Login → Search Application → Upload Documents → Uploaded list — and using
Apple's built-in **VisionKit Document Camera** (`VNDocumentCameraViewController`)
for scanning: no third-party SDK, no license key, live edge detection,
auto-capture, perspective correction and multi-page scanning all handled by
iOS itself.

## ⚠️ Unverified — no Xcode/macOS available here

This code was written without access to a Mac or Xcode, so **it has not been
compiled or run**. Every API used is long-stable (VisionKit's document
scanner API is unchanged since iOS 13; the SwiftUI APIs used here have all
been stable since iOS 15/16), and it was written carefully with that in mind,
but you should expect to fix at least minor build errors — the way the Android
build needed a couple of real fixes even after careful review, this is that
same step, just one you'll have to run yourself in Xcode.

## What's here

This is **source files only** — no `.xcodeproj`. That's deliberate: an Xcode
project file is a fragile, hand-editable-but-easy-to-corrupt format, and with
no way to open or validate one here, generating one blind was a bigger risk
than just giving you clean source to drop into a project Xcode scaffolds
itself.

```
ios/DocUpload/
  DocUploadApp.swift          — @main app entry
  Info.plist                  — reference: camera usage description key
  Models/Document.swift       — the document record (metadata; files stored separately)
  Stores/
    SessionStore.swift        — demo login state (hdfc / 123)
    DocumentStore.swift       — local JSON + file-based persistence, simulated "upload"
  Scanning/
    DocumentScannerView.swift — VNDocumentCameraViewController wrapper (the scanner)
    FilePickerView.swift      — "Upload from Mobile" (Files app picker, images + PDF)
  Views/
    RootView.swift            — login gate + NavigationStack
    Route.swift                — navigation destinations
    LoginView.swift / SearchView.swift / UploadView.swift / UploadedView.swift
    Components.swift          — shared document row / empty state
  Utilities/Formatters.swift  — byte size / date formatting
```

## Setup

1. In Xcode: **File → New → Project → iOS → App**.
   - Product Name: `DocUpload`
   - Interface: **SwiftUI**
   - Language: **Swift**
   - Bundle Identifier: `com.hdfc.docupload` (to match the web/Android apps)
   - Minimum Deployment: **iOS 16.0**
2. Delete the auto-generated `ContentView.swift` and the default `@main` App
   file Xcode created.
3. Drag the `ios/DocUpload/` folder from this repo into the project navigator
   (check "Copy items if needed" and add to the app target).
4. Add camera permission: select the app target → **Info** tab → add a new
   key **Privacy - Camera Usage Description** (`NSCameraUsageDescription`)
   with a value like "Camera access is used to scan documents." (If your
   project uses a standalone `Info.plist` instead of the auto-generated one,
   merge in the key from `ios/DocUpload/Info.plist` instead.)
5. Build and run **on a physical device** — the simulator has no camera, so
   the scan flow can't be exercised there at all (gallery/PDF upload and the
   rest of the flow work fine in the simulator).

## Notes

- No backend: "upload" is simulated (a delay + progress animation that marks
  documents as uploaded locally), matching the web/Android demos.
- Documents (metadata + files) persist in the app's local Documents directory
  across launches, but are private to this app (no iCloud/sync).
- `VNDocumentCameraViewController.isSupported` is checked before presenting
  the scanner, falling back to an alert (with the option to use the file
  picker instead) if scanning isn't available on the device — mirroring the
  graceful-degradation pattern in the web and Android scanners.
