# Third-party notices

Songlane's source, design, icon composition, procedural cover art and demo audio are original. No competitor source or artwork is incorporated.

Optional linked recording metadata comes from MusicBrainz core data under CC0. Only recording/artist identities, credits, durations, disambiguation, ISRCs and artist relationships are retained. Supplementary tags, ratings, annotations and search scores are not retained. The web service has separate access terms and rate/identification requirements. See `docs/METADATA_SOURCE.md`; no MusicBrainz server code, artwork or audio is included.

Runtime libraries are obtained from their Maven distributions. License and copyright notices in those distributions retain force; the MIT license for Songlane does not replace them.

- AndroidX Activity, Core, Lifecycle, Compose UI, Compose Material/Material3, Material Icons, DocumentFile and Media3: Android Open Source Project / Google and contributors, Apache License 2.0. https://android.googlesource.com/platform/frameworks/support/ and https://github.com/androidx/media
- Kotlin standard library and kotlinx.coroutines: JetBrains and contributors, Apache License 2.0. https://github.com/JetBrains/kotlin and https://github.com/Kotlin/kotlinx.coroutines
- Guava and ListenableFuture (transitive): Google and contributors, Apache License 2.0. https://github.com/google/guava
- Additional transitive dependencies: see generated `docs/sbom.cdx.json`. Missing/inherited Maven license fields are recorded as unverified rather than assigned an invented license.

Test tools (not part of the release app): JUnit 4 (Eclipse Public License 1.0); AndroidX Test and Compose Test (Apache License 2.0); their transitive dependencies retain their notices. Gradle wrapper is a Gradle project tool under Apache License 2.0. Android SDK and JDK are external build tools, not copied into the repository.

Apache License 2.0 text is included in `licenses/Apache-2.0.txt`. The resolved Maven artifact license inventory must be reviewed before a production release. `./gradlew exportSbom` records exact runtime component versions and SHA-256 artifact hashes. It does not constitute a vulnerability scan or legal opinion.
