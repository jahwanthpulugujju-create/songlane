# Changelog

## 0.2.0 — 20 October 2026

- Added optional MusicBrainz core-data recording search/details with lookup consent, identified/rate-limited requests, explicit recording confirmation, source attribution and offline linked credits.
- Added source-backed shared-contributor edges to local Sonic Compass routes; unconfirmed/imported identities cannot create those edges.
- Added bounded ReplayGain metadata readers and opt-in track/album PCM gain processing tied to decoded stream identity, with peak-based boost limiting and source/processing diagnostics.
- Fixed Passport custom-label restoration, indexed ambiguous matching and duplicate playlists on repeated import; tested a 2,000-record fixture.
- Made independent song edits preserve fresh metadata/lyrics instead of replacing stale objects.
- Expanded validation to 35 unit tests and 22 instrumented tests. Release signing and the remaining roadmap/release gates are still open.

## 0.1.0 — 20 October 2026

- Initialized the `songlane` Git repository and original Android application.
- Added local library intake, search, grouping, favorites, editable ordered playlists, and Media3 playback/queue/resumption.
- Added fair shuffle policies, Rediscover and validated explainable Sonic Compass routes with feedback and route saving.
- Added local Session Director and optional bounded Anthropic intent parsing with explicit consent and Android Keystore protection.
- Added Taste Passport v1, history privacy/private sessions, full data deletion and user-controlled source access.
- Added local ID3/sidecar lyrics support, playback speed/pitch and silence controls, media browsing, widget and quick settings entry points.
- Added original procedural artwork and eight opt-in original generated sound sketches.
- Added unit/instrumented tests, build workflow, source decisions, security notes, license notices and dependency SBOM generation.

Validation and production-readiness limits are recorded in `docs/VALIDATION.md` and `docs/ROADMAP.md`.
