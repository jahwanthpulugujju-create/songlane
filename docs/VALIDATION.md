# Validation

Validated on 20 October 2026 using Windows, JDK 17.0.20.1, Gradle 9.3.1, AGP 9.1.1, Android SDK 36 and a Pixel_7a x86_64 Android 14/API 34 emulator. No physical device was attached. The emulator ran without host audio output.

## Executed results

- Debug APK: built, installed and launched successfully. The Windows build/install/launch helper was also exercised against the emulator.
- Unit tests: **19 passed, 0 failed**. Fifteen discovery/search/input tests cover all shuffle policies across multiple seeds, uniqueness, artist separation, recent exclusions, unknown explicit/language/year metadata, duration limits, route validation, rediscovery ordering, multilingual/fuzzy search and bounded local intent/input handling. Four lyric tests cover LRC stamps/offsets, plain text, malformed bounds and embedded USLT.
- Instrumented tests: **9 passed, 0 failed**. Full UI flow imports the eight opt-in audio sketches, plays/favorites a song, builds a route and navigates to the profile. MediaBrowser verifies the local browse tree, real ExoPlayer playing state, queue movement/removal/seek/pause, timer configuration and queue persistence. Seven storage/security tests cover database recreation, export/import matching and URI rejection, malformed/oversized imports, history privacy, atomic private-queue clearing/stale-save rejection, library-reference deletion and Keystore encryption/deletion.
- Android lint: **0 errors, 21 warnings**. Remaining warnings include newer dependency/tool versions, KTX/style suggestions, synchronous preference commits, the intentionally exported media service (controller trust is checked in code), and resource suggestions. Full report is included in `validation/lint.txt`; it has not been suppressed with a baseline.
- Optimized release APK: `assembleRelease` succeeded, including R8 and release vital lint. It is **unsigned** and was not installed or published. The separate debug APK is signed with the development key and is installable.
- Dependency inventory: 93 resolved runtime components with SHA-256 artifact hashes; direct or verified inherited Maven POM license evidence was resolved for all 93. This is a component inventory, not a vulnerability scan or legal certification.
- Local source secret-pattern scan found no matches for representative API-key/private-key patterns. This is not proof against every secret format.
- Kotlin sources formatted with ktfmt 0.64, whose downloaded artifact SHA-256 was checked against Maven Central before execution. No external CodeRabbit review was run because its CLI was not installed; the agent performed the local implementation/privacy review.

The final combined command was `gradlew.bat --max-workers=4 :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug :app:assembleRelease` and returned **BUILD SUCCESSFUL**. Raw build and XML reports are in `validation/`.

## Fixes verified during the run

Rediscover had a mixed Integer/Long comparator default and was corrected to a Long default. Lint found Android 8 incompatibilities in retriever closure and a navigation-bar style attribute; both were fixed without raising minimum SDK. Private mode now clears the saved queue in the same database transaction and prevents stale snapshots from restoring it. Sleep timer requests are confined to in-process state instead of a mutable command on the exported media-service intent. Arbitrary controller-supplied audio URIs are resolved back to known local track IDs.

## Limits and remaining release validation

The suite was run on one emulator. Bluetooth, calls/audio focus on real hardware, headphone unplug, Android Auto vehicle/DHU operation, widget/quick-settings host interaction, multiple OEM battery policies, large-library performance, auditory quality, TalkBack/manual accessibility and broader Android-version behavior have not been certified. Static minimum-API lint passed; this does not substitute for running Android 8.

The cloud provider path was implemented from its documented Messages API but no paid live request was made, since the user supplied no API key. Local mode remains usable. There is no verified currency-based in-app spending cap; bounded calls/tokens and provider-side billing limits are documented.

No Play Store listing, production signing, naming clearance, external listener interviews, formal legal review or public backend has been completed. The source is a working local-player beta, not evidence that every future P2/P3 ambition in the supplied blueprint is shipped.
