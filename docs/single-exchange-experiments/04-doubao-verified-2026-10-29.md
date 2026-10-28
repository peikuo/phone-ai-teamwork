# Doubao public single-exchange result — 2026-10-29

This is one real-device result for the fixed public English UI test. It is not evidence of general Doubao automation, three-app collaboration, or five-case repeatability.

| Field | Observed result |
| --- | --- |
| Device | Xiaomi 23013RK75C, Android 15, USB ADB |
| Target | Doubao `com.larus.nova`, version `14.9.0 (14090001)` |
| Local model | Qwen3-VL-2B-Instruct GGUF through the on-device llama.cpp backend |
| Input | Canonical 277-character public payload staged and exactly read back before this send |
| Send | Local model proposed the visible `发送` ImageView in 39,883 ms; Agent and Guardrail checked the fixed task, bound window, payload, fresh target and live node before one Android click |
| Verification | Two fresh accessibility observations matched the exact user bubble, assistant body, and answer completion controls; title text was excluded from the assistant-body match |
| Stored result | Task `COMPLETED`, turn `CAPTURED`, pending send intent cleared; answer `POC_OK_7319` saved as a Doubao message in Room |
| Product display | The task page showed `Verified test response · 豆包`, `POC_OK_7319`, and `Saved in Phone AI Teamwork` after reopening the app |

The first reply-verifier pass rejected the completed page because two version-specific static UI nodes were not yet recognized: a hidden call label and the assistant answer's own accessibility description. That false negative retained the pending send intent and caused no resend. After the profile was narrowed to the exact observed nodes, read-only verification completed. There was one real send click and several read-only verification attempts.

The operator manually opened a new Doubao conversation before the earlier staging step. The debug path uses one fixed public prompt and does not accept arbitrary outgoing text. Temporary cropped screenshots used for diagnosis were kept outside Git and removed; no personal conversation content is included here.
