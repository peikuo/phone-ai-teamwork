# Kimi and Doubao public input staging — 2026-10-29

Two fixed public English prompts were staged on a Xiaomi Android 15 device. This is input verification only: **neither prompt was sent**, and no reply was captured.

| Target app | Local-model step | Stored result | Verified input |
| --- | --- | --- | --- |
| Kimi `3.1.3 (378)` | 56,185 ms, UI tree, no image | `PAUSED / STAGED`, pending intent cleared | 277 characters, SHA-256 `c2180aea36ef4198a7ca2f82b36ca1b7c1fdacf7e27ec2b52dfd86a931506e2c` |
| Doubao `14.9.0 (14090001)` | 50,473 ms, UI tree, no image | `PAUSED / STAGED`, pending intent cleared | 277 characters, SHA-256 `9338645bb30bac78f2904bd4985f5b59934bff4d34fa3e51c2dbe02b3944e82e` |

The two hashes differ because each canonical payload contains its own task and step identifiers. Both payloads derive from the same fixed public question and were written by the app after an on-device model proposal, fresh rebinding, Guardrail checks, an intent committed to Room, and a live-node check.

In both apps, writing expanded the composer. The initial version-specific profile rejected the changed layout, leaving the WRITE intent pending and the task in `NEEDS_USER`; the Agent did not retry the write. The recovery verifier later matched the exact canonical draft in two fresh observations of the bound window, then atomically changed the task to `PAUSED / STAGED` and cleared the intent. Kimi's expanded composer exposed two new static descriptions. Doubao exposed two new descriptions and moved one hidden static call label from path `14/0` to `13/0`; only these observed states were added to the pinned profiles.

Manual preparation remains outside the automated result: the operator switched Kimi to text mode and opened a new empty Doubao conversation. The send controls shown after staging have not been mapped to executable roles, and the three-app workflow is still unverified. Temporary cropped diagnostics were kept outside Git and removed.
