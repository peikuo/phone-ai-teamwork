# Kimi and Doubao input proposals — 2026-10-29

These are read-only on-device model trials. They establish input location on two prepared screens, not permission to write or send.

| App | Prepared page and observation | On-device model result | Duration |
| --- | --- | --- | --- |
| Kimi `3.1.3 (378)` | Operator switched the app's voice composer to text mode. The app's AccessibilityService captured 41 nodes, `truncated=false`, with one visible, empty, editable input. | Qwen3-VL-2B-Instruct proposed `FOCUS_INPUT` at that input; `selected_editable=true`. | 49,773 ms step, 2,218 ms load |
| Doubao `14.9.0 (14090001)` | The app opened in an old conversation. Operator tapped the app's new-chat control. A fresh 15-node, `truncated=false` sample had one hidden and one visible empty editable input. | The same model proposed `FOCUS_INPUT` at the visible input; `selected_editable=true`. | 30,240 ms step, model reused |

Both calls used the UI tree without an image. Neither executed the proposal. No message was written or sent. The Kimi voice-mode switch and Doubao new-chat navigation were manual preparation and must be implemented or clearly counted as manual steps in any future scenario result. Temporary cropped screenshots for diagnosis were kept outside Git and removed; the prior Doubao conversation content was not saved in the repository.

Before an executable write adapter is enabled for either version, it needs a version-specific empty-thread profile, exact payload and target rebinding, fresh UI checks, and a recorded effect intent. Sending additionally needs an observed send control and a role/completion verifier with a no-resend recovery path.
