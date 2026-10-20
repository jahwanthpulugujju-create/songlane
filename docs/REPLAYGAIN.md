# Local ReplayGain processing

ReplayGain is opt-in, with Off, Per song and Per album modes. The app reads existing numeric gain/peak tags; it does not measure loudness, modify audio files, infer mastering quality, normalize cloud/provider audio or claim a bit-perfect output path.

Supported readers are bounded to 2 MB of metadata/initial file scanning: uncompressed ID3v2.3/v2.4 TXXX frames; FLAC Vorbis-comment blocks; Ogg Vorbis/Opus ReplayGain comments; and WAV files with an ID3 chunk within that bound. Unsupported tags/encodings, oversized/truncated metadata, R128-only values and late WAV chunks remain unknown. No assumptions about the R128 reference level or Opus output-gain interaction are made.

Gain values must be finite and within -60 to +30 dB. Peak values must be finite and positive. Album mode uses album gain when present, otherwise a song gain tag. Missing gain leaves the level unchanged. Negative gain can be applied without a peak tag; positive gain requires a reported peak and is bounded against that peak. Saturation prevents integer overflow if a peak tag is inaccurate; it does not make an inaccurate tag correct or guarantee undistorted acoustic output.

The Media3 PCM processor resolves its media ID from AudioProcessor.StreamMetadata at the actual stream flush, after the prior stream has drained. It does not select the next song's coefficient from an early UI transition. This matters for gapless buffering. Unknown stream identity keeps unity gain. Mode/metadata edits refresh the currently bound stream, without rebinding its identity. The app's audio-information panel displays the file-reported tags and supported PCM processing state separately.

The pipeline uses Media3's normal PCM conversion path. Tests cover 16-bit and float sample math, unity byte preservation, saturation, malformed bounds, supported tag formats, empty pipeline input, mode reset, stream switching, and real ExoPlayer playback of a generated tagged WAV followed by an untagged file. Physical loudness, DAC output, Bluetooth and listening quality are not certified by those tests.

Protocol/API references inspected during implementation:

- https://id3.org/id3v2.3.0
- https://id3.org/id3v2.4.0-frames
- https://www.rfc-editor.org/rfc/rfc7845 — R128/Opus distinctions; not implemented as an inferred ReplayGain conversion.
- AndroidX Media3 1.11.1 `BaseAudioProcessor`, `AudioProcessor.StreamMetadata` and `DefaultAudioSink` source distributions from Google's Maven repository.
