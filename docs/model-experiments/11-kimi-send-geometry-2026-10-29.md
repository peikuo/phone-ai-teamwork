# Kimi send geometry check — 2026-10-29

The phone's accessibility service was restored by the operator. A one-shot, text-only observation of Kimi `3.1.3 (378)` returned 43 nodes without truncation. The visible EditText held a 277-character draft. This metadata check did not compare its text with the stored payload; no screenshot or message body was exported.

| Node | Path | Bounds | Clickable | Enabled and visible |
| --- | --- | --- | --- | --- |
| Draft EditText | `0/0/0/0/0/0/0/0/0/0/1/1/0/0` | `90 1896 935 2118` | yes | yes |
| `发送讯息` label | `0/0/0/0/0/0/0/0/0/0/1/1/3/0` | `930 2156 1034 2260` | no | yes |
| Label parent | `0/0/0/0/0/0/0/0/0/0/1/1/3` | `909 2145 1034 2270` | no | not independently proven |

The model's earlier `FOCUS_SEND` proposal localized the label. The label's reported action list lacks click, and both the label and its direct parent report `clickable=false`; the parent's action list was not captured. The current Guardrail cannot authorize `SUBMIT`. A coordinate gesture would require a separate, version-pinned effect path with live window and label verification, an exact persisted send intent, and independent reply evidence. No gesture was attempted here. The screen was turned off after the observation, as requested.

Later that night, after the screen was restored at the operator's request, a new debug-only read-only check compared the staged Kimi draft with the canonical Store payload twice, 750 ms apart. Both fresh observations matched the exact 277-character payload and the bound empty thread. The task state was unchanged before and after the observations. The report was `state=VERIFIED`, `submitted=false`, `storeChanged=false`; no message or screen content was exported. This closes the exact-draft evidence gap for the current staged task, but does not establish the send icon's touch target or authorize a gesture.
