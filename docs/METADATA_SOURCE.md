# MusicBrainz source adapter — 20 October 2026

MusicBrainz is an optional metadata source. It supplies no audio, playback entitlement, cover art, lyrics or streaming downloads. The base player continues to work offline.

## User and source boundaries

The user opens song details, chooses recording lookup, enters a contact email or HTTPS support/project URL, and grants consent for that lookup session. Search sends only the edited recording title/artist and the configured User-Agent contact. Subsequent detail lookup sends only the chosen recording ID and contact. No listening history, audio, local paths, model key or taste profile is sent. No background/batch enrichment or periodic polling occurs.

The contact is required by MusicBrainz's identification guidance. It is stored locally for reuse and excluded from Taste Passport exports. A public release should provide the project's own monitored contact rather than requiring each user to configure one. Do not invent a maintainer address.

The HTTPS endpoint is fixed to musicbrainz.org. Redirects are disabled. Responses are bounded to 512,000 bytes, requests have connection/read timeouts, and a process-wide mutex enforces at least 1.1 seconds between request starts. HTTP 429/503 starts a cooldown using the Retry-After seconds value (bounded to one hour) or a 60-second fallback. There are no automatic retries or hidden queued bulk requests.

## Data and identity

Search results are candidates. A user must open a candidate, retrieve its recording details, and explicitly confirm that it is the same performance/version as the local file. This is a user-confirmed identity link, not an acoustic fingerprint match. Audio URIs, local canonical file tags and track IDs are not replaced or merged. The link can be removed.

Only core recording fields are retained: MBID, title, credited artists, duration, disambiguation, ISRCs and artist relationships. Each retained link carries fetched/confirmed timestamps and a derived MusicBrainz source URL. Source-provided tags, genre associations, annotations, ratings, search scores and arbitrary URLs are discarded. No cover art is fetched. Confirmed common contributor IDs may provide an attributed edge in Sonic Compass; the reason states the source and that the recordings were linked by the listener.

MusicBrainz identifies these core fields and relationships as CC0. Supplementary information, including genre/tag associations and search indexes, has different terms. The adapter does not retain search scores or supplementary fields. Non-commercial API access is free; commercial use of the web service requires reviewing MetaBrainz's plan/permission requirements separately from the data license.

## Primary references

- https://musicbrainz.org/doc/MusicBrainz_API
- https://musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting
- https://musicbrainz.org/doc/MusicBrainz_API/Search/RecordingSearch
- https://musicbrainz.org/doc/MusicBrainz_Database
- https://musicbrainz.org/doc/About/Data_License

These references were inspected during implementation. No MusicBrainz server code or assets are incorporated. API availability is independent of local playback; missing or ambiguous metadata remains missing instead of being invented.
