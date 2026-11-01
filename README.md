# GB Voice Bridge

Turns the assistant button on a Zepp OS watch (tested: Amazfit Band 7) into private dictation, with
[Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge) instead of the Zepp app.
Gadgetbridge sends the watch's voice to this app; the app transcribes it on the phone and the text
appears on the watch. Optionally each dictation goes to your own webhook, whose reply is shown instead.

Needs a Gadgetbridge build with the Zepp OS voice-service change (Codeberg PR #7233).

## Features
- On-phone speech recognition with sherpa-onnx: Parakeet 0.6B (v2 English, v3 European languages) and
  Whisper tiny/base/small (English). Models download in the background and are checksum-checked.
- Or "transcribe on your server": the audio goes straight to your webhook.
- Dictation log with playback, copy, resend and delete; word replacements.
- Webhook: JSON with the transcript and optional WAV audio, bearer token stored encrypted
  (format in [WEBHOOK.md](WEBHOOK.md)).
- Only apps you allow, checked by signing certificate, can send audio.

## Install
Download the APK from [Releases](https://github.com/AJV009/gb-voice-bridge/releases). Android 9 or newer, 64-bit ARM.
Release APKs are signed with this certificate (SHA-256):
`01:4E:3C:E7:DC:28:AA:22:AF:A3:07:46:08:A2:53:0E:DE:12:3D:2A:9A:9F:B4:28:29:4D:C6:6D:18:F6:39:A8`
Check with `apksigner verify --print-certs <apk>`.

## Setup
1. Install the APK, open the Speech tab and download a model (Parakeet v2 is the fast choice).
2. In Gadgetbridge: watch settings → Developer settings → Voice Assistant, set
   package `com.ajv009.gbvoice` and class `com.ajv009.gbvoice.VoiceService`.
3. Use the assistant on the watch once, then allow Gadgetbridge in the app's Log tab.

## Privacy
With on-phone transcription, audio and text never leave the phone. The only network use is downloading models
from Hugging Face and, if you turn it on, your own webhook. No analytics, no accounts.

## Build
`./gradlew assembleDebug` (JDK 17+, Android SDK 37). arm64 only.
`app/libs/` holds the sherpa-onnx AAR (static onnxruntime) from the sherpa-onnx releases.
Release builds are shrunk with R8 (`./gradlew assembleRelease`) and signed outside Gradle with `apksigner`.

## How this was made
Written with the help of an AI coding assistant (Claude Code), directed, reviewed and tested by me on an
Amazfit Band 7 (firmware 1.11.0.1) and a Nothing Phone 3a Pro (Android 16). JVM unit tests: `./gradlew testDebugUnitTest`.

## Licence
Apache License 2.0, see [LICENSE](LICENSE). Third-party code and model licences are listed in [NOTICE](NOTICE).
