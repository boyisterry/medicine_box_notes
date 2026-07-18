# MedicineBoxNotes Android

A native, offline-first Android app that gathers a family's scattered paper records, prescriptions, medicine labels, and dose schedules into one searchable, on-device home-health book — with grounded, privacy-preserving AI that never sends your data to the cloud.

[中文](./README.zh-CN.md) | **English** · [Product and technical specification](./doc/方案设计文档.md)

## Why MedicineBoxNotes

Home healthcare is still oddly analogue. Medical records live on loose sheets of paper scattered across hospitals and drawers; the family medicine cabinet is a black box; and the people who need care most are the ones most likely to miss a dose. MedicineBoxNotes was built around four everyday frictions:

- **Scattered records.** Paper records are easy to lose and hard to piece together — one family member's history may sit across several hospitals, never in one place.
- **Forgotten inventory.** You buy a medicine only to find an unopened box of the same one at the back of a shelf. A clear view of what you keep at home prevents duplicate purchases.
- **Missed doses.** Chronic medication only works if it's taken on time. A today's checklist keeps each day's doses visible.
- **No way to ask your own records a question.** "What did we take for that cough last year?" should be answerable in seconds, not by rifling through folders.

The medicine box becomes a single, calm place to see how much backup medicine you have on hand, read each drug's leaflet (camera + OCR + AI), and check off today's doses.

Because medical records and prescriptions are deeply personal, the app never uploads them to answer a question. A local large language model (Gemma 4 E2B via LiteRT-LM) runs entirely on-device for natural-language Q&A — you keep full ownership of your private data while still getting fast, conversational answers grounded in your own records. When no model is present, a built-in rule engine keeps every feature working.

## Features

Five tabs — Home, Records, Medicine, Query, Settings — built in Jetpack Compose.

**Home / today**
- Bilingual date and time-of-day greeting.
- Today's medication checklist, derived from each medicine's scheduled times and dose plan; tap to check off a dose (writes an idempotent medication log).
- Follow-up countdown to the nearest upcoming visit.
- Low-stock panel (stock ≤ 5) and a family quick-entry row.

**Records**
- Full CRUD for family members, medical records, prescriptions, and image attachments, backed by Room.
- Records grouped by visit date and year, with member filter chips.
- Camera (CameraX) and system Photo Picker capture; private image + thumbnail storage with bundled ML Kit OCR (Chinese-then-Latin fallback).
- Per-field OCR smart-fill that fills only empty fields, never overwriting your input.
- "Add to medicine box" on each prescription line, carrying provenance (hospital, visit date, member) into the cabinet item.
- A4 PDF export of a record, shared via the system share sheet.
- Follow-up reminders: two local alarms per follow-up date — the day before and the day of, both at 09:00 — rebuilt after reboot or timezone change and deep-linking into the record.

**Medicine cabinet**
- One list of every home medicine with stock badges, AI summaries, and scan counts.
- Scan a drug's box front / back / side / leaflet; OCR text is combined per medicine without leaking the scan-type label into the name.
- AI smart-organize turns raw OCR into structured name / dosage / frequency / duration — but only when you tap the button; the app never auto-writes AI fields.
- Manual stock and dose-plan editing (scheduled times, start/end, active toggle). Stock estimates derive from frequency × duration.
- Low-stock warnings (stock ≤ 5).

**Query (on-device AI)**
- Natural-language Q&A over your records, attachment OCR/AI text, and cabinet, answered by Gemma 4 E2B on-device.
- Answers stream in with a processing animation, a grounded indicator, and tappable citations.
- A risk-acknowledgement gate on every AI entry; cancellable mid-stream.
- Anti-hallucination by design: seven system-prompt hard rules, nullable JSON output, a sanitize chain, and a strict safety gate that rejects any non-cited answer that isn't an honest "no evidence / don't know".

**Settings**
- In-app language switch across seven locales: English (default), Simplified Chinese, Japanese, French, German, Spanish, Korean.
- Family-member management.
- One-tap Gemma model download (resumable, SHA-256 verified) with start / pause / resume / delete.
- Encrypted backup export and import.

**Cross-cutting**
- Offline-first: business data, images, OCR, and AI inference stay on-device by default.
- AES-256-GCM encrypted backup (`.mbn`) via the system Storage Access Framework, with PBKDF2 key derivation (180k iterations), a SHA-256 file manifest, and UUID-merge import.
- Warm, paper-inspired Compose design system: warm-beige background, white 26-dp cards, brick-red primary, a dedicated AI accent, and a forced light theme.

## Architecture

A small Gradle multi-module project — no DI framework, no image-loading library, and a single `MainViewModel` that aggregates Room `Flow`s into `StateFlow`s.

```text
app/                UI, navigation, camera, reminders, PDF, backup, model download
core-model/         Domain models and medication/stock rules (pure Kotlin/JVM)
core-database/      Room entities, DAOs, transactions, repository
core-designsystem/  Design tokens and reusable Compose components
core-ai/            ML Kit OCR, rule-based AI, and Gemma LiteRT-LM client
```

A few decisions worth calling out:

- **Soft-anchored medicine cabinet.** Medicines and dose logs carry *no* foreign keys to records, prescriptions, or members — only soft-anchor columns. Deleting a record or member detaches the cabinet item rather than deleting it, so your manually-maintained stock and dose history never disappear by accident.
- **Provider abstraction with rule fallback.** A single `MedicalAiClient` interface has two implementations: a rule engine that is always available, and the Gemma client that activates once the model is ready and transparently falls back to the rule engine on any error or safety-gate violation. The app is never unusable because of a missing or failed model.
- **GPU → CPU engine fallback.** The Gemma engine initializes on GPU and drops back to CPU if the device cannot, so the same code path runs across arm64 hardware.
- **Anti-hallucination, layered.** Hard prompt rules → nullable JSON output + sanitize → a Q&A safety gate that throws on any answer with zero citations that isn't an explicit refusal. Internal UUID tags are stripped from citations before they reach the UI.
- **Resumable, verified model download.** HTTP Range resume gated by an `If-Range` ETag check, a 6 GB free-space requirement, a ≥2 GB size floor, and a post-download SHA-256 verify against a pinned digest.

## Tech stack

Kotlin 2.2.10 · JDK 17 · AGP 8.13.0 · Gradle 8.13. Jetpack Compose (BOM 2025.08.01) + Material 3, Navigation Compose 2.9.3, Lifecycle 2.9.2, Room 2.8.4, CameraX 1.4.2, ML Kit Text Recognition 16.0.1 (Latin + Chinese), LiteRT-LM 0.13.0, OkHttp 4.12.0, kotlinx-coroutines 1.10.2, kotlinx-serialization 1.9.0, DataStore 1.1.7, WorkManager 2.10.3. minSdk 26 (Android 8.0); compile and target SDK 36. Gemma 4 E2B (~2.6 GB, `.litertlm`) from `huggingface.co/litert-community/gemma-4-E2B-it-litert-lm`.

## Build

Point `local.properties` at Android SDK 36 (`sdk.dir=...`), then:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Full verification:

```bash
./gradlew :core-model:test :core-ai:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
```

On external macOS volumes the project redirects every module's build output to `/private/tmp/medicine-box-notes-build/${project.name}` to sidestep AppleDouble metadata issues, so the debug APK lands at:

```text
/private/tmp/medicine-box-notes-build/app/outputs/apk/debug/app-debug.apk
```

The release APK is R8-minified but unsigned until you supply a signing configuration. Gemma is downloaded at runtime and is not bundled in the APK; without a model or on unsupported hardware, the app stays in rule-based mode and every feature remains available.

## Current boundaries

- **No account, no cloud database, no cross-device sync** in this version — all data lives on one device.
- **No medication dose alarms.** Today's doses are an in-app checklist on Home; only follow-up (return-visit) reminders fire system notifications.
- **No expiry-date tracking.** The cabinet tracks stock and low-stock warnings, not best-before dates.
- **Partial localization.** Only English is complete; the six other locales fall back to English for missing keys, and follow-up notifications and PDF output contain hard-coded Chinese regardless of the selected language.
- **AI/OCR results are organization and search aids, not medical advice** — always verify against the original material before use. Every page keeps the original image and raw OCR text for that reason.
