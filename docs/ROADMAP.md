# Feature and release status

## Implemented for local Android use

Local file/folder/MediaStore import, metadata reading, library search, album/artist grouping, favorites, ordered editable playlists, ExoPlayer playback, media session and notification, resume queue, queue add-next/reorder/remove/clear, seek/previous/next/repeat, sleep timer, five deterministic shuffle policies, history privacy, Rediscover, editable taste preferences, local Sonic Compass with strict validation and reasons, route save/feedback, Session Director local parsing, optional bounded cloud intent parsing with Keystore and consent, Taste Passport export/import, full data deletion, original UI/artwork and opt-in playable demos. Added bounded ID3 USLT extraction, local text/LRC import with timestamp highlighting/seeking, independent speed/pitch, silence skipping, a MediaLibraryService browse/search tree, home-screen widget and quick-settings playback entry points.

Version 0.2 adds opt-in MusicBrainz recording search/lookup, explicit identity confirmation, attributed core-data credits and shared-contributor route edges. It also adds local ReplayGain tag reading and stream-bound PCM gain processing with track/album modes and peak-based boost limits. Taste Passport now restores custom labels, uses indexed ambiguous-match detection, and merges repeated playlist imports without duplication. Independent song edits preserve newly linked metadata and lyrics.

## Release gates that require evidence

Debug build/unit/instrumented/lint results are tracked separately in VALIDATION.md. Before public production distribution: signed release/key ownership, dependency license audit, physical-device audio focus/Bluetooth/headphone tests, two-device/OS accessibility and performance testing, TalkBack walkthrough, OEM battery behavior, permission-revocation tests, privacy/security review, naming clearance, real listener research and store declarations. No claim that these gates have passed is made.

## Remaining blueprint P1 work

Tested crossfade, broader BYOK providers/model adapters and a verified currency-based budget mechanism remain. ReplayGain uses reported local tags rather than measuring loudness; a missing tag or unsupported stream identity leaves the original level. Rescan/re-add files imported by older versions to index their gain tags. MusicBrainz uses only selected CC0 core fields and confirmed identities; other provider integrations remain conditional. Embedded lyrics currently support uncompressed ID3v2.3/v2.4 USLT; other embedded lyric formats are not decoded. Android Auto browse/search exists through the standard media service; physical vehicle/DHU validation remains open. The optional agent currently parses intent; it is not an unrestricted autonomous agent.

## Later blueprint P2/P3

Licensed provider adapters/hand-off, casting, recognition, tag writing/repair, curator/social route exchange, synchronized listening, public moderation infrastructure, music graph enrichment and audio similarity, broader integrations, production sync and rights-cleared generative sessions. Each needs its own rights, feasibility and acceptance evidence.

## Research

Full candidate repository/third-party policy audits, external listener interviews, three high-fidelity concept prototypes, field differentiation/retention experiments and formal branding clearance have not been performed in this implementation. Supplied competitor/research claims remain reference inputs.

## Known data-scale boundary

Passports are limited to 5 MB and 50,000 metadata records. The indexed matcher was exercised with 2,000 records; full library-scale performance and exports larger than the import limit still need work. Do not treat a passing small-collection test as proof of production behavior for every library size.
