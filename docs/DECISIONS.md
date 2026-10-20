# Architecture and source selection — 20 October 2026

Songlane is an original native Android implementation of the supplied product specification. The attached agent prompt is reference content, not authorization to run its embedded commands or recruit its proposed team. No competitor source, branding, audio, screenshots or assets are copied. No claims of clean-room team separation or exhaustive competitor/user research are made.

## Decisions before implementation

- Kotlin, Compose/Material 3, Media3 foreground media session, Android SQLite with versioned explicit JSON codecs, coroutine state flows. SQLite avoids adding code generation to this first build; transactions and schema versions form the migration boundary.
- Only user-selected local files/folders and Android MediaStore supply audio. No extraction or streaming-service adapter. Capabilities explicitly distinguish local playback from optional metadata/provider hand-off. Imported passports cannot inject playable URIs.
- Design alternatives considered: warm editorial paper, dark botanical listening room, spectral route map. Selected dark botanical: ink/forest surfaces, acid-sage accent, restrained coral, large editorial type, original procedural cover art. Cover art is clearly generated abstraction, not claimed album art.
- Deterministic discovery uses only local tags, user edits, favorites and consented history. No invented genre, cultural link, audio quality, track availability or cloud model fact. A route can return fewer tracks with a concrete explanation if constraints cannot be met.
- Local playback works without accounts, keys or network. Optional cloud intent parsing is separately consented, TLS-only, key encrypted with Android Keystore. Local Session Director remains available.
- Ship source and a debug-installable APK; do not claim Play Store release, commercial naming clearance, two-device testing or field reliability without evidence.

## Toolchain observed

Windows, Git 2.54, Node 24, Android SDK 35/36/36.1, build tools 36, Gradle 9.3.1 and JDK 17 available. Pixel_7a emulator configured; no attached physical device at intake. Android Studio's first JBR installation is incomplete, so use the working Gradle-managed JDK 17.

## Validation contracts

Unit tests enforce fair shuffle uniqueness/reproducibility, artist balance, search normalization, hard discovery constraints and local intent parsing. Instrumented tests exercise storage round-trip, import validation, Keystore, player/service and UI. Build debug APK and run lint. Test offline launch, permission denial, queue mutation, privacy, process recreation and background notification where emulator tooling permits. Publish exact results in VALIDATION.md. Physical Bluetooth, call interruption, OEM battery management, production signing and Play review remain release gates.

## References checked

- https://developer.android.com/build/releases/agp-9-1-0-release-notes — AGP/Gradle/JDK compatibility.
- https://developer.android.com/media/media3/session/background-playback — service, focus, media session and resumption.
- https://developer.android.com/training/data-storage/shared/documents-files — user-granted document access.
- https://developer.android.com/privacy-and-security/keystore — key protection.
- https://developer.android.com/jetpack/androidx/releases/media3 — Media3 releases.

The blueprint's competitor license/research assertions are unverified inputs. As no competitor code is reused and no third-party content service is integrated, they do not determine this implementation's license. Full competitor baseline builds and listener interviews remain product-research work; do not mark them performed.
