# MedicineBoxNotes Android

A native, offline-first Android app for family medical records, prescriptions, medicine inventory, medication check-ins, follow-up reminders, OCR, and grounded on-device AI.

[中文](./README.zh-CN.md) | **English** · [Product and technical specification](./doc/方案设计文档.md)

## Why MedicineBoxNotes

Home healthcare is still oddly analogue. Medical records live on loose sheets of paper, scattered across hospitals, clinics, and drawers; the family medicine cabinet is a black box; and the people who need care most are the ones most likely to forget a dose. MedicineBoxNotes was built to fix four everyday frictions:

- **Scattered records.** Paper medical records are easy to lose and even harder to piece together — one family member's history may be spread across several hospitals and never gathered in one place.
- **Forgotten inventory.** You buy a medicine, only to discover an unopened box of the same one at the back of a shelf. A clear view of what you actually keep at home prevents duplicate purchases.
- **Expired medicine.** Without a list it's hard to know what has quietly passed its expiry date. The app tracks shelf life and warns you in time.
- **Missed doses.** Chronic medication only works if it's taken on time. Reliable reminders keep daily medication on schedule.

The medicine box becomes a single, calm place to see how much backup medicine you have on hand, read each drug's instructions and leaflet, and get a dependable nudge when it's time to take it.

Because medical records and prescriptions are deeply personal, the app never uploads them to the cloud to be answered. Instead it runs a local large language model on-device for natural-language Q&A — you keep full ownership of your private data while still getting fast, conversational answers grounded in your own records.

## Implementation

- Five functional tabs: Home, Records, Medicine Box, Query, and Settings.
- English by default, with persistent in-app switching for Simplified Chinese, Japanese, French, German, Spanish, and Korean.
- Full Room-backed CRUD for family members, records, attachments, prescriptions, medicines, medication logs, and scan assets.
- CameraX, Photo Picker, private image storage, and bundled Chinese/Latin ML Kit OCR.
- Follow-up alarms, daily medication tasks, low-stock warnings, A4 PDF export, and AES-256-GCM encrypted backups.
- Always-available rule engine plus optional arm64 LiteRT-LM/Gemma inference with resumable model download, GPU-to-CPU fallback, streaming, cancellation, citations, and safety gates.
- Warm paper-inspired Compose design system derived from the supplied iOS assets and design tokens.

## Stack

Kotlin 2.2, JDK 17, Android Gradle Plugin 8.13, Jetpack Compose, Material 3, Room, CameraX, ML Kit, and LiteRT-LM. Minimum API 26; compile and target API 36.

```text
app/                UI, navigation, camera, reminders, PDF, backup, model download
core-model/         Domain models and medication/stock rules
core-database/      Room entities, DAOs, transactions, repository
core-designsystem/  Design tokens and reusable Compose components
core-ai/            OCR, rule-based AI, and Gemma LiteRT-LM client
doc/                Product specification and supplied visual assets
```

## Build

Configure Android SDK 36 in `local.properties`, then run:

```bash
./gradlew :app:assembleDebug
./gradlew :core-model:test :core-ai:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

On external macOS volumes the project redirects build outputs to `/private/tmp/medicine-box-notes-build` to avoid AppleDouble metadata issues. The release APK is unsigned until a private signing configuration is supplied.

Gemma is downloaded at runtime and is not bundled in the APK. Core local data and rule-based features remain available without a model or network connection.
