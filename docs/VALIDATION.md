# Validation

Version 0.2.0 validated on 20 October 2026 using Windows, JDK 17.0.20.1, Gradle 9.3.1, AGP 9.1.1, Android SDK 36 and a Pixel_7a x86_64 Android 14/API 34 emulator. No physical device was attached. The emulator ran without host audio output.

## Executed results

- Debug APK: built, installed and launched successfully. The Windows build/install/launch helper was also exercised against the emulator.
- Unit tests: **35 passed, 0 failed**. The original 19 discovery/search/input/lyrics tests still pass. Seven metadata-policy tests cover contact/query validation, concurrency/rate/cooldown behavior, recording identity and attributed route edges. Nine ReplayGain tests cover bounded tag readers, track/album/peak policy, unity sample preservation, PCM scaling, saturation and malformed input.
- Instrumented tests: **22 passed, 0 failed**. The app UI and media browsing/queue flow still pass. New tests verify consent before network dispatch, fixed endpoints/identified requests, response identity and discarded supplementary fields, cooldown without automatic retries, stream-bound gain and empty audio-pipeline buffers. A real ExoPlayer test imports a generated tagged WAV, verifies -6 dB processing for its decoded stream, then switches to an untagged song and verifies the original-level fallback. Twelve storage/security tests cover recreation/privacy/Keystore plus linked metadata/gain round-trip, contact exclusion, repeated Passport import, long/custom tags, ambiguous/duplicate IDs, 2,000-record indexed matching and independent song edits.
- Android lint: **0 errors, 22 warnings**. Remaining warnings include newer dependency/tool versions, KTX/style suggestions, synchronous preference commits, the intentionally exported media service (controller trust is checked in code), and resource suggestions. Full report is included in `validation/lint.txt`; it has not been suppressed with a baseline.
- Optimized release APK: `assembleRelease` succeeded, including R8 and release vital lint. It is **unsigned** and was not installed or published. The separate debug APK is signed with the development key and is installable.
- Dependency inventory: 93 resolved runtime components with SHA-256 artifact hashes; direct or verified inherited Maven POM license evidence was resolved for all 93. This is a component inventory, not a vulnerability scan or legal certification.
- Local source secret-pattern scan found no matches for representative API-key/private-key patterns. This is not proof against every secret format.
- Kotlin sources formatted with ktfmt 0.64, whose downloaded artifact SHA-256 was checked against Maven Central before execution. No external CodeRabbit review was run because its CLI was not installed; the agent performed the local implementation/privacy review.

The final combined command was `gradlew.bat --max-workers=4 :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug :app:assembleRelease` and returned **BUILD SUCCESSFUL** for v0.2.0. Raw build and XML reports are in `validation/`. The Windows helper then rebuilt the unchanged debug target, installed it and launched the updated app successfully.

## Fixes verified during the run

Rediscover had a mixed Integer/Long comparator default and was corrected to a Long default. Lint found Android 8 incompatibilities in retriever closure and a navigation-bar style attribute; both were fixed without raising minimum SDK. Private mode now clears the saved queue in the same database transaction and prevents stale snapshots from restoring it. Sleep timer requests are confined to in-process state instead of a mutable command on the exported media-service intent. Arbitrary controller-supplied audio URIs are resolved back to known local track IDs.

The new audio processor initially mishandled the shared empty pipeline buffer; real playback tests caught it, and a dedicated regression now passes. Gain is resolved from Media3 stream metadata at flush boundaries rather than the early UI track transition. Passport imports previously omitted user labels and duplicated playlists; they now preserve bounded labels and merge stable playlist IDs. Independent field edits update the latest stored track instead of overwriting a newly attached recording link or lyric.

## Limits and remaining release validation

The suite was run on one emulator. Bluetooth, calls/audio focus on real hardware, headphone unplug, Android Auto vehicle/DHU operation, widget/quick-settings host interaction, multiple OEM battery policies, large-library performance, auditory quality, TalkBack/manual accessibility and broader Android-version behavior have not been certified. Static minimum-API lint passed; this does not substitute for running Android 8.

The cloud provider path was implemented from its documented Messages API but no paid live request was made, since the user supplied no API key. Local mode remains usable. There is no verified currency-based in-app spending cap; bounded calls/tokens and provider-side billing limits are documented.

MusicBrainz client parsing, consent, endpoint, identification and rate policy were exercised with injected transport fixtures. A public search endpoint was inspected during development, but no end-to-end live Android lookup with a user-supplied monitored contact has been claimed. Normalization tests cover PCM math and stream integration, not measured acoustic loudness or subjective audio quality.

No Play Store listing, production signing, naming clearance, external listener interviews, formal legal review or public backend has been completed. The source is a working local-player beta, not evidence that every future P2/P3 ambition in the supplied blueprint is shipped.
