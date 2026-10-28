# Kimi and Doubao send-control localization — 2026-10-29

These are read-only model proposals on the already staged fixed public English drafts. **No send control was clicked.** They are not authorization to send or proof that either app can return a verified reply.

| App and version | Successful on-device proposal | Step time | Scope |
| --- | --- | --- | --- |
| Kimi `3.1.3 (378)` | `FOCUS_SEND`, observed `发送讯息` node, bounds `922 2164 1027 2269` | 61,375 ms | UI tree only; no action |
| Doubao `14.9.0 (14090001)` | `FOCUS_SEND`, observed `发送` node, bounds `932 2179 1048 2326` | 54,463 ms | UI tree only; no action |

The first tree trials returned JSON with only `action` and `node`; the parser required a third `payload` key and paused. A later diagnostic showed this exact schema mismatch without logging model output. The parser now accepts the two-key form only when the permitted actions are payload-free locator or pause actions. `WRITE` and `SUBMIT` still require their exact payload. After that bounded change, each app produced one valid `FOCUS_SEND` proposal. A Kimi image-assisted trial timed out after about 90 seconds, so the positive evidence here is only from the UI tree.

The read-only experiment now closes the local model after each run to release its large memory allocation. One broadcast following an app switch did not start a new report until the debug APK was reinstalled; the cause was not established. Repeatability and unattended execution remain unverified. Before a real send, a pinned app profile must map these labels to an actionable control, verify the canonical staged input and bound conversation in fresh observations, persist a send intent, and verify the user message and complete assistant reply without automatic resend.

Later step-lab runs on the same staged public Kimi draft returned three consecutive tree-only `FOCUS_SEND` proposals. Each run used a fresh observation that the app itself checked against the canonical payload and bound empty thread before passing it to the model. Mean step time was 50,855 ms (maximum 52,285 ms); no screenshot, gesture, or click was used. These are repeat localization samples on one draft, not three independent conversations. The accessibility send label remains nonclickable and its first clickable ancestor covers the whole composer, so execution stays blocked. See [step-lab](../step-lab.md) and [Kimi geometry](11-kimi-send-geometry-2026-10-29.md).
