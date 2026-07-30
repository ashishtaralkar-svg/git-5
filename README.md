# HDFC DocUpload — Mobile Document Upload (Android)

A production-oriented Android application for uploading and scanning documents
against a banking application number. Built with **Kotlin + Jetpack Compose**,
Material 3, and a clean **MVVM + Repository** architecture. The scanning
experience is powered by **Google ML Kit Document Scanner** (`SCANNER_MODE_FULL`),
delivering an Adobe Scan / Microsoft Lens-class flow: automatic edge & document
detection, auto-capture, auto-crop, perspective correction, background removal,
image enhancement, and Color / Grayscale (B&W) / Original filters with
multi-page support.

> **Note:** This project targets Android and must be built with the Android SDK
> (Android Studio Koala or newer, or the command-line SDK). The Gradle wrapper is
> included. The sandbox where this was authored has no Android SDK, so it has not
> been compiled there — open it in Android Studio and run on a device/emulator.

## User flow

```
Login → Search Application → Upload Documents
                                  ├── Upload from Mobile (gallery: images + PDF, multi-select)
                                  └── Scan Document (camera opens directly, auto-detect)
                                          → Preview → Add More / Back
                              → Upload (enabled when ≥1 document)
                              → Success → View Uploaded Documents / Upload More
```

**Demo credentials:** Username `hdfc` · Password `123`

## Tech stack

| Concern | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 |
| Architecture | MVVM, Repository pattern, unidirectional state |
| Async / state | Coroutines, StateFlow |
| DI | Hilt |
| Navigation | Navigation Compose (animated transitions) |
| Local storage | Room (documents, status, application number) |
| Camera / scan | ML Kit Document Scanner (CameraX under the hood) |
| Gallery | Storage Access Framework (`OpenMultipleDocuments`) |
| Networking (placeholder) | Retrofit + OkHttp over HTTPS |
| Offline retry | WorkManager (Hilt worker) |
| Image loading | Coil |
| Secure session | EncryptedSharedPreferences (token only; no passwords) |

## Module / package layout

```
com.hdfc.docupload
├── DocUploadApplication        # @HiltAndroidApp + WorkManager config
├── MainActivity                # Compose host + splash + edge-to-edge
├── navigation/                 # Screen routes, animated NavHost
├── ui/
│   ├── theme/                  # Material 3 light & dark color schemes, type
│   ├── components/             # PrimaryButton, DocumentCard, LoadingOverlay…
│   ├── login/                  # LoginScreen + LoginViewModel
│   ├── search/                 # SearchScreen + SearchViewModel
│   ├── upload/                 # UploadScreen, UploadViewModel, DocumentScanner
│   └── uploaded/               # UploadedDocumentsScreen + ViewModel
├── domain/
│   ├── model/                  # Document, Resource, enums
│   └── repository/             # Repository interfaces
├── data/
│   ├── local/                  # Room entity, DAO, database, converters
│   ├── local/prefs/            # SessionManager (encrypted)
│   ├── remote/                 # Retrofit API service interfaces + DTOs
│   └── repository/             # Repository implementations
├── di/                         # Hilt modules (App, Network, Repository)
├── util/                       # FileUtils, ImageCompressor, NetworkMonitor…
└── worker/                     # UploadWorker (offline retry)
```

## Key requirements → where they live

- **Login validation & "Invalid Username or Password."** — `LoginViewModel`, `AuthRepositoryImpl`.
- **Alphanumeric-only application number + blank validation** — `SearchViewModel`.
- **Upload from gallery (images + PDF, multi-select, previews, remove)** — `UploadScreen`, `UploadViewModel.addFromGallery`.
- **Scan with auto edge/crop/perspective/enhancement + Color/B&W/Original** — `DocumentScanner.kt` (`SCANNER_MODE_FULL`).
- **Add More Documents (re-opens camera), temporary list, Back enables Upload** — `UploadScreen` + Room-backed pending list.
- **Upload progress + success screen (View Uploaded / Upload More)** — `UploadViewModel.upload`, `SuccessDialog`.
- **Uploaded list: thumbnail, name, size, date/time, delete, preview** — `UploadedDocumentsScreen`, `DocumentCard`.
- **Offline storage + auto-retry on reconnect** — `DocumentRepositoryImpl.scheduleRetry` + `UploadWorker` (WorkManager network constraint).
- **Image compression before upload** — `ImageCompressor`.
- **Secure token storage, no passwords, clear on logout** — `SessionManager`.
- **Light & dark mode, Material 3, rounded buttons, animated transitions** — `ui/theme`, `AppNavHost`, `components`.

## Future backend integration

Service interfaces are ready in `data/remote/ApiServices.kt`:

- `POST /login` — `AuthApi`
- `POST /searchApplication` — `ApplicationApi`
- `POST /uploadDocuments` (multipart) — `DocumentApi`
- `GET  /uploadedDocuments` — `DocumentApi`

Point `BASE_URL` (in `app/build.gradle.kts`) at the real HTTPS host and replace
the demo bodies in the repository implementations — the UI/ViewModels are
transport-agnostic.

## Building

```bash
# Requires Android SDK; set sdk.dir in local.properties or ANDROID_HOME.
./gradlew assembleDebug
./gradlew test            # JVM unit tests
```

## Permissions

Requested only when needed and handled gracefully:

- **Camera** — managed by the ML Kit scanner activity.
- **Gallery** — none required (Storage Access Framework).
- **Network state** — used to gate uploads / schedule retries.
