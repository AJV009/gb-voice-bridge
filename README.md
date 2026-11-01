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

## Setup
1. Install the APK, open the Speech tab and download a model (Parakeet v2 is the fast choice).
2. In Gadgetbridge: watch settings → Developer settings → Voice Assistant, set
   package `com.ajv009.gbvoice` and class `com.ajv009.gbvoice.VoiceService`.
3. Use the assistant on the watch once, then allow Gadgetbridge in the app's Log tab.

## Build
`./gradlew assembleDebug` (JDK 17+, Android SDK 37). arm64 only.
`app/libs/` holds the sherpa-onnx AAR (static onnxruntime) from the sherpa-onnx releases.
