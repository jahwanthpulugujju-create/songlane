# songlane

An original Android music player for your local files, with fair shuffle, explainable listening routes, and a portable Taste Passport. Kotlin + Compose + Media3. No login, ads, telemetry or bundled API key.

## Download on Android

[Download Songlane v0.3.0 APK](https://github.com/jahwanthpulugujju-create/songlane/releases/download/v0.3.0/songlane-v0.3.0.apk) · [Release notes](https://github.com/jahwanthpulugujju-create/songlane/releases/tag/v0.3.0)

Requires Android 8.0+. Open the APK on your phone and allow installation from your browser/file manager when Android asks. This is a signed, optimized beta with ARM64, ARMv7, x86 and x86_64 support. It plays your local music and includes optional original demo audio; no streaming catalog is included.

If you installed an earlier development-signed APK, export your Taste Passport and uninstall that build before installing this first release-signed version. Future published APKs must reuse the same release key to update in place.

## Run

Open this folder in Android Studio. Install JDK 17, Android SDK 36 and Build Tools 36.0.0. Set `ANDROID_HOME` or create an ignored `local.properties` with `sdk.dir`.

```sh
./gradlew testDebugUnitTest assembleDebug
./gradlew connectedDebugAndroidTest
./gradlew lintDebug exportSbom
node scripts/resolve-licenses.mjs
```

On Windows use `gradlew.bat`. The wrapper downloads pinned Gradle 9.3.1. Install `app/build/outputs/apk/debug/app-debug.apk` on Android 8.0 or newer. The generated debug APK is for development, not a production-signed store release.

For signed distribution, build `assembleRelease`, then use `scripts/sign-apk.ps1` with your existing private keystore and `SONGLANE_SIGNING_PASSWORD` set in the process environment. Keep signing material outside the repository. See [release signing](docs/RELEASE.md).

Run instrumented tests on a dedicated emulator/test device: the suite resets the debug app's library/preferences while setting up its fixtures.

With one authorized Android device/emulator connected, Windows users can build, install and launch with `powershell -ExecutionPolicy Bypass -File scripts/run-android.ps1`. With multiple devices, add `-DeviceSerial <adb-serial>`.

## Use

1. Choose audio files, choose a folder, scan the device music library, or load eight original sound sketches from **Listen**. Songlane indexes files in place; it does not copy or modify your original audio.
2. Play a song. Tap the mini player for seeking, previous/next, repeat, sleep timer, queue, local lyrics and source information. Import a text/LRC lyric file when embedded USLT lyrics are unavailable. Playback continues through a foreground Media3 session and Android's media controls; local browsing/search, widget and quick-settings entry points are included. Independent speed/pitch and silence skipping are in Settings.
3. Browse songs, albums, artists and playlists in **Library**. Track menus add next, enqueue, save to playlists, exclude from discovery and edit local tags/ratings. Playlist details provide ordering, renaming and removal.
4. Open **Compass**, choose an anchor, destination tags and exploration level, then set duration and strict filters. Routes only include available local songs. Unknown ratings/language/year are excluded when their filter requires known values. Reasons cite local tags/history; no acoustic or cultural relationship is invented.
5. Use **You** to edit preferred genres and avoided artists, start a private session, or export a Taste Passport. Settings provide history pause/delete, passport import, source management, speed, silence skipping and full data deletion.
6. **Session Director** parses simple listening requests locally. Optional Anthropic/OpenAI cloud parsing offers reviewed models, separate encrypted keys, a request preview and consent. Settings → **Manage cloud options** configures daily/monthly USD ceilings and timeout. Conservative reservations are counted before sending; unresolved requests retain them. Cloud output only fills bounded Compass settings; song selection and validation stay local. Provider charges may differ from this ledger; use provider billing controls too. See [cloud setup and spending policy](docs/CLOUD_AGENT.md).
7. In a song's details, choose **Find recording details** for optional MusicBrainz metadata. Supply a contact required by the source's User-Agent policy, consent to the lookup, review candidates and explicitly link the right performance/version. Core CC0 credits remain available offline after linking. Confirmed shared contributors can explain a Sonic Compass connection. File tags and audio are not rewritten.
8. In Settings, select **ReplayGain → Per song / Per album** to use existing local gain tags. Tags are read from supported ID3, FLAC, Ogg Vorbis/Opus and bounded WAV-ID3 metadata. Rescan or re-add files imported by earlier versions. Positive gains require a peak tag; sample saturation prevents integer wrap if tags are incorrect. This is tag-based processing, not loudness analysis or a bit-perfect path.

## Data & privacy

The versioned SQLite store contains the library index, favorites, playlists, user tags, preferences and up to 20,000 listens. A listen requires at least 10 seconds of actual playback. Private sessions and paused history do not record listens; private mode also clears the saved queue. Android backup is disabled. There is no analytics SDK. API keys are AES-GCM encrypted with a non-exportable Android Keystore key and excluded from exports.

Taste Passport v1 is a JSON export with metadata, playlists, favorites, preferences and history. It excludes file paths, audio, lyrics, request-contact preferences and secrets. Import matches local recordings by ID or exact title/artist/album plus duration; ambiguous matches remain unmatched. It restores user tags, genre/language/year/rating labels and merges playlists by stable ID, so repeated import is idempotent for those playlists. It cannot inject audio URIs, grant filesystem access, import keys or trust imported recording-identity links. Those links require fresh source review. Import is limited to 5 MB, 50,000 metadata records, 20,000 listens and 1,000 playlists. Export/import concerns portability of user data, not ownership of copyrighted recordings.

## Scope and honest limits

This is an Android local-player implementation of the supplied Music Discovery OS concept. It does not include a licensed streaming catalog, provider extraction, public social service, casting, audio recognition, tag writing, a measured audio embedding model, generative music or production cloud sync. Those are the blueprint's later/conditional roadmap. Read [ROADMAP.md](docs/ROADMAP.md), [DECISIONS.md](docs/DECISIONS.md) and [VALIDATION.md](docs/VALIDATION.md) for implementation and release status.

Source/design/audio are original; no competitor app is forked. The working product name has not been commercially cleared. Third-party libraries and notices are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md); `exportSbom` generates a dependency inventory with artifact hashes and license evidence from local Maven POMs.

## Demo audio

The eight 45-second mono PCM sketches are original mathematical oscillator compositions, not commercial recordings. Regenerate them with `node scripts/generate-demo.mjs`. Genre labels describe these generated sketches; they are not inferred descriptions of imported audio. Cover art is generated vector geometry, not claimed album artwork.

## License

Original Songlane code, procedural artwork and demo generator/audio are offered under the MIT license. Dependencies retain their own licenses and notices. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
