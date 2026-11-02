# Webhook format (`gbvoice/1`)

GB Voice Bridge POSTs each watch dictation to your URL as JSON.

## Request
Headers:
- `Content-Type: application/json; charset=utf-8`
- `Authorization: Bearer <token>`, only when you set a token in the app
- `Idempotency-Key: <id>`, the same as `id`, so a resend from the log can be recognised

Body:
```json
{
  "format": "gbvoice/1",
  "id": "5b1d…",
  "recorded_at": "2026-11-01T17:20:00.000Z",
  "source": "watch",
  "duration_ms": 14000,
  "engine": "parakeet-0.6b-v2",
  "transcript": "add milk to the shopping list",
  "audio": { "content_type": "audio/wav", "sample_rate": 16000, "data": "<base64 WAV>" }
}
```
- `engine` and `transcript` are `null` when the phone is set to leave transcription to your server.
- `audio` is present only when "Include the audio" is on, or when the phone does not transcribe.
  The audio is 16-bit mono WAV at 16 kHz, about 450 KB for 14 s (about 600 KB as base64).
- **Saved webhooks and keyword routes** (Webhook tab): each server is saved once (name, URL, secret). The default
  webhook gets every dictation that no keyword route takes. A dictation that starts with a route's keyword
  ("Organizer, add milk") goes to that route's webhook instead. `transcript` is then the text after the keyword
  ("Add milk"), and a `route` object says what was cut (`webhook` is the saved webhook's name):
  ```json
  "route": { "webhook": "TickTick Organizer", "keyword": "Organizer", "full_transcript": "Organizer, add milk" }
  ```
  `keyword` is the keyword as it was heard. Matching ignores case, punctuation and spacing around the keyword
  ("organizer.", "Organizer:", "to-do" = "to do") and up to two filler words in front ("Hey organizer, …"),
  but the word itself must match. Dictations that match no keyword go to the default webhook without `route`
  (or nowhere, when the default webhook is "Nothing").
- The "Send a test" button sends `"source": "phone_mic"` and `"test": true`. A server should answer it (for example
  "Connected to …") without acting on the transcript. Real dictations never carry `test`.

## Response
- Any 2xx counts as delivered. Anything else is logged as "not sent" and can be resent from the log.
- To show something on the watch, answer within about 12 s with JSON `{"title": "...", "text": "..."}`
  (either field optional) or with plain text. Up to 1000 characters are used.
- An empty body shows the transcript (or "Sent" when there is none).
- Answers that arrive later than about 12 s still land in the log, but the watch has moved on by then.
