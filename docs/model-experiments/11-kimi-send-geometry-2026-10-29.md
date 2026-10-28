# Kimi send geometry check — 2026-10-29

The phone's accessibility service was restored by the operator. A one-shot, text-only observation of Kimi `3.1.3 (378)` returned 43 nodes without truncation. The visible EditText held a 277-character draft. This metadata check did not compare its text with the stored payload; no screenshot or message body was exported.

| Node | Path | Bounds | Clickable | Enabled and visible |
| --- | --- | --- | --- | --- |
| Draft EditText | `0/0/0/0/0/0/0/0/0/0/1/1/0/0` | `90 1896 935 2118` | yes | yes |
| `发送讯息` label | `0/0/0/0/0/0/0/0/0/0/1/1/3/0` | `930 2156 1034 2260` | no | yes |
| Label parent | `0/0/0/0/0/0/0/0/0/0/1/1/3` | `909 2145 1034 2270` | no | not independently proven |

The model's earlier `FOCUS_SEND` proposal localized the label. The label's reported action list lacks click, and both the label and its direct parent report `clickable=false`; the parent's action list was not captured. The current Guardrail cannot authorize `SUBMIT`. A coordinate gesture would require a separate, version-pinned effect path with live window and label verification, an exact persisted send intent, and independent reply evidence. No gesture was attempted here. The screen was turned off after the observation, as requested.
