# Signed Android beta

The public GitHub repository and downloadable APK were explicitly authorized by the project owner. v0.3.0 is a direct-download GitHub beta, not a Play Store launch or a claim that every roadmap feature is complete.

The optimized APK is built from the release variant with R8, package `app.songlane`, version name `0.3.0`, code `3`, minimum Android API 26 and target API 36. It includes arm64-v8a, armeabi-v7a, x86 and x86_64 libraries and is not debuggable. It is signed with a dedicated RSA-3072 Songlane key. Android apksigner verified its v2/v3 signatures. Public certificate SHA-256:

`2cd9aa4117d7a2ddfb95137602792648b016207075e557b6cf3fc02727e33a8e`

The private keystore/password are outside the repository and must never be committed, attached to a release or put in a public source archive. The local password file uses Windows DPAPI protection for the current Windows account/machine. It is not a portable password backup; the owner should retain the key and password in secure storage before moving machines. Future updates must retain the release signing identity.

## Build and sign

Run `gradlew.bat assembleRelease` with JDK 17 and Android SDK 36. The default build produces an unsigned APK; no signing secrets are bundled in Gradle files or CI. Set `SONGLANE_SIGNING_PASSWORD` only in the signing process environment. Run:

```powershell
./scripts/sign-apk.ps1 -KeyStore <private-keystore-path> -OutputApk <output-apk-path>
```

The helper uses Android Build Tools 36.0.0, signs without logging passwords, verifies the result and writes a SHA-256 checksum beside it. It does not generate/recreate keys. Do not modify an APK after signing. The unsigned default CI artifact is a separate debug development build signed by the standard debug configuration; it must not replace the published release artifact.

## Install and update

Download the APK from the GitHub release on an Android 8.0+ phone. Open it and grant that browser/file manager permission to install when Android requests it. No account is needed for the public download or local playback. Android may scan a new, infrequently installed app; the app is not enrolled in Google Play distribution.

An earlier development-signed Songlane cannot be updated by this new release key. Export a Taste Passport, uninstall the earlier build, install the signed release, grant audio access/re-add files, then import the Passport. The original audio files are never deleted by uninstalling Songlane. Future release-signed updates can install in place.

See VALIDATION.md for tests and runtime evidence, CLOUD_AGENT.md for optional API charges, and ROADMAP.md for remaining features and field-validation gates. No private signing material belongs in any GitHub asset.
