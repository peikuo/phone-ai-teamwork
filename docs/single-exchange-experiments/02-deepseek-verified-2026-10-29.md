# DeepSeek public single-exchange result — 2026-10-29

This is one real-device result for a fixed, public English prompt. It does not establish the three-app workflow or five-case repeatability.

| Field | Observed result |
| --- | --- |
| Device | Xiaomi 23013RK75C, Android 15, USB ADB |
| Target | DeepSeek `com.deepseek.chat`, version `2.5.2 (273)` |
| Local model | Qwen3-VL-2B-Instruct GGUF through the on-device llama.cpp backend |
| Prompt | `This is a public UI exchange test. Reply with exactly POC_OK_7319. Do not search or add an explanation.` |
| Send decision | Local model proposed the visible `Send` control; Agent and Guardrail checked the exact staged payload and fresh target before one Android click |
| Reply verification | Two fresh accessibility observations matched the exact user message, assistant message, and completed response controls; no generation control was present |
| Stored result | Task `COMPLETED`, turn `CAPTURED`, pending send intent cleared; answer `POC_OK_7319` saved as a DeepSeek message in Room |
| Product display | The task page in Phone AI Teamwork showed `Verified test response · DeepSeek` and `POC_OK_7319` after reopening the app |

The model's send-control proposal passed three consecutive read-only trials before the one real send. The UI verifier initially rejected the completed page because it did not recognize DeepSeek's static empty-input hint. That false negative left the send intent pending and did not resend. After adding the exact observed hint to the version-specific allowlist, the read-only verification completed. This is one successful send and capture, with several verifier attempts.

The temporary screenshots used to diagnose the page were cropped below the status bar, kept outside Git, and are not demonstration media. The repository contains only the public prompt, answer, and non-sensitive evidence summary.
