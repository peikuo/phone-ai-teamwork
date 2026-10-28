# Planning and Result Flow

## Product outcome

The user's final answer belongs in Phone AI Teamwork. The app must save the verified synthesis response in its own Room task state and show it on the task screen after the user returns to the app or restarts it. A report in the developer's Codex chat is diagnostic evidence, not the product output.

The external AI apps do the substantive answering, review, and synthesis. The small on-device model helps operate their visible interfaces. Phone AI Teamwork owns the task, permissions, workflow, evidence, and final result.

## Three different plans

| Plan | Owner during the POC | Runtime boundary |
| --- | --- | --- |
| Experiment plan: which cases to run, in what order, and how to judge them | Developer/Codex, recorded in a versioned case manifest | Test harness only; it cannot claim that the phone planned the task |
| Task plan: which AI app answers, reviews, receives which source, and synthesizes | Deterministic `Recipe` in Phone AI Teamwork | Frozen before sending; visible to the user; validated against participant and sharing grants |
| UI action plan: how to complete the current authorized step on the current screen | On-device model proposes one bounded action at a time | Agent checks state; Guardrail authorizes; Phone Tools executes and verifies |

The local model does not need to invent a seven-step business workflow. For the five POC cases, the app can use the existing 3 + 3 + 1 discussion Recipe. Codex may prepare the public English questions and expected answers as test data, but Codex does not operate the phone in place of the local model or supply a missing external AI response.

If later tasks need a custom workflow, a selected external AI app may draft a *proposal*. Phone AI Teamwork must parse it into a limited plan schema, check participants, source sharing, turn and action budgets, show it to the user, and freeze the approved plan. The external reply has no authority to click, send, add participants, or broaden sharing. This future planner is not a prerequisite for the fixed POC Recipe.

## End-to-end handoff

1. The user enters an English question and chooses Kimi, Doubao, DeepSeek, and a final synthesizer in Phone AI Teamwork.
2. Workflow creates and stores the seven-step plan, source grants, and exact payload templates. The user can inspect the plan and what each app will receive.
3. AgentLoop selects the current step. Phone Tools observes the target app. The on-device model proposes a limited UI action or pauses.
4. Guardrail checks the current task, app, conversation, payload, source grants, and fresh target. Phone Tools executes an allowed action and records objective evidence. An uncertain send pauses without automatic resend.
5. A version-specific verifier confirms that the exact user message arrived in the intended conversation and that the complete assistant reply has finished. State saves the reply, role, source IDs, and evidence before the next step can use it.
6. After the seventh response is verified, State marks the task `COMPLETED` and points the result view to that step's `responseMessageId`. The task screen displays the full English answer, its source app, and the seven-step provenance. The answer remains available when the app is reopened.

An output displayed only inside Kimi, Doubao, or DeepSeek does not satisfy step 6. A screenshot or a model's claim that generation has finished does not establish a complete response.

## Implementation order and gates

| Gate | Required evidence | Next work unlocked |
| --- | --- | --- |
| Device ready | New debug APK installed; accessibility service enabled; model files present; three app versions recorded; phone and Mac remain powered and connected | Device experiments |
| One app exchange | DeepSeek input, exact send confirmation, assistant role, completion, full text, and Store readback | Generalize the exchange adapter |
| Three app exchange | The same evidence for Kimi and Doubao, including clean text mode and popup handling | Cross-app handoff |
| Conversation return | Re-enter each task conversation without mixing another task or an old draft | Review and synthesis steps |
| Final result | Verified synthesis response saved in Room; task page shows it after app restart | One seven-step scenario |
| Repeatability | Five public English questions each pass all seven steps with no unmarked manual action, wrong conversation, duplicate send, or missing result | Claim five successful demonstrations |

Each gate records the responsible component, app and model versions, successful runs and total attempts, manual preparation, timing, and failure reason. A test harness or ADB action may inspect evidence but must not be counted as an on-device model action.

## Overnight run and demonstration

The overnight runner should checkpoint every step and use bounded waits and retries. It should stop on a lock screen, unknown page, changed app version, missing accessibility service, memory failure, uncertain send, incomplete answer, or disconnected device. On reconnection it reads the persisted task and verifies any uncertain effect before resuming. It must never create five apparent successes from synthetic or manually copied replies.

The five questions use invented, non-sensitive data and English prompts and responses. Low-resolution diagnostic screenshots stay in Git-ignored `captures/`. After successful device runs, record short clips of the real app flow, crop the entire phone status bar and any personal UI, inspect the exported frames for private content, and upload only the reviewed clips. The architecture diagram should show the six responsibilities and the final result returning to Phone AI Teamwork. Publishing media to a private GitHub repository and making that repository public are separate steps.

## Current gap — updated 2026-10-29

The first one-app exchange gate has a real-device positive result: DeepSeek fixed public prompt staging, one model-selected send, complete answer verification, Room capture, and display in Phone AI Teamwork. See [the device evidence](single-exchange-experiments/02-deepseek-verified-2026-10-29.md). Kimi and Doubao have each staged the fixed public prompt with exact readback, stopping before send; see [their device evidence](single-exchange-experiments/03-kimi-doubao-staged-2026-10-29.md). Their send and reply verifiers, conversation return, seven-step orchestration, five-case repeatability, and privacy-reviewed demonstration media remain open.
