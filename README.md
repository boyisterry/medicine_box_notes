# MedicineBoxNotes · Family Medicine Cabinet & Medical Records

> An iOS health-tracking app for the home that brings every family member's **medical records / prescriptions / test attachments / home medicines** into one place.
> **Offline-first, locally traceable, and assisted by on-device AI** — both your data and the AI stay on your device.

[中文](./README.zh-CN.md) | **English**

📖 Full product & technical spec (in Chinese): [`doc/方案设计文档.md`](./doc/方案设计文档.md)

---

## The Problems It Solves

| Pain point | How the app handles it |
|---|---|
| Medical info is scattered across paper slips and chat logs; nothing is findable at follow-up visits | Digitize every document with the camera → automatic OCR + AI structuring → archive per family member, with full-text search |
| Can't answer the doctor's "what have you taken / been treated for before" | Records sorted newest-first and grouped by year, searchable attachments, prescriptions traceable to the medicine cabinet |
| Home medicines run out or expire unnoticed | Cabinet stock management + low-stock alerts (≤5) + medication plans that drive today's to-do |
| Forgetting follow-up appointments | After a follow-up date is set, local notifications fire **the day before 9:00 AM + the day of 9:00 AM**, deep-linking into the record |
| "What did my daughter take for her cough last year?" means manual digging | The bottom "Query" tab: local retrieval recall + on-device Gemma generating answers **with cited sources** |
| Health privacy is too sensitive for cloud AI | Local storage by default; AI inference runs **entirely on-device**; the only network call (DuckDuckGo) sends just the user's question — never any records/medicine data |
| AI hallucinates, which is dangerous in healthcare | End-to-end anti-hallucination: hard prompt rules + all-optional JSON + "none / don't know" normalization + query safety gate + risk acknowledgement + "to be verified" marking |

---

## Core Features

- **Three core assets**: Medical records (`MedicalRecord`), home medicine cabinet (`MedicineItem`), family members (`FamilyMember`), plus the derived medication log (`MedicationLog`).
- **Input pipeline**: photo → Vision OCR → on-device AI structuring → user confirmation → saved.
- **Usage pipeline**: today's to-do / follow-up reminders / low stock / natural-language local Q&A on the home screen.
- **5-tab layout**: Home / Records / Cabinet / Query / Settings (native SwiftUI, iOS 17+).

---

## Tech Stack

| Layer | Choice |
|---|---|
| Platform / language | iOS 17+ / Swift 5 |
| UI | SwiftUI (custom floating TabBar, paper-feel card system) |
| Data | SwiftData (`@Model`), CloudKit-compatible for sync |
| OCR | Apple Vision (`VNRecognizeTextRequest`, Chinese + English) |
| On-device model | Gemma 4 E2B (`.litertlm`, ~2.58 GB) + LiteRT-LM, bridged via `GemmaLiteRtBridge.xcframework` |
| Web search | DuckDuckGo Instant Answer API (manual opt-in on the Query tab, 5s timeout, question only) |
| Model delivery | Runtime download (Hugging Face / HF-Mirror, resumable) |

---

## Design Principles

1. **Offline-first, locally traceable** — sensitive health data stays on-device by default; AI inference runs locally; networking is optional and minimal.
2. **AI is a constrained organizer, not a free-reasoning assistant** — it only summarizes / extracts / organizes / retrieves / answers; it never diagnoses, prescribes, or invents.
3. **Results must be verifiable** — original images and OCR text are always retained; AI output is marked "to be verified"; answers come with cited sources.

Anti-hallucination is the most technically substantive part of the project (three layers: hard prompt rules → output validation & normalization → query safety gate). See [Section 4](./doc/方案设计文档.md) of the spec.

---

## Project Structure

```text
medicine_box_notes/
├── MedicineBoxNotes.xcodeproj/
├── Frameworks/
│   └── GemmaLiteRtBridge.xcframework          # LiteRT-LM ObjC++ bridge (arm64 device + simulator)
├── MedicineBoxNotes/
│   ├── MedicineBoxNotesApp.swift               # Entry + all SwiftData models + main pages + inline services
│   ├── MedicalAI*.swift                        # On-device AI abstraction (facade / prompts / state / models)
│   ├── GemmaEngine.swift                       # On-device inference actor engine
│   ├── Services/                               # Model download / AI config / web search / today's plan / image preprocessing
│   ├── Views/                                  # HomeView + Settings subpages
│   ├── Components/                             # 15 paper-feel components
│   └── Theme/                                  # Design tokens (color / font / metrics / member palette)
├── medicine_box_design/                        # Design prototypes (steady + bold variants)
└── doc/
    └── 方案设计文档.md                          # ← Full spec (in Chinese; source of truth)
```

---

## Build & Run

1. Open `MedicineBoxNotes.xcodeproj` in **Xcode**.
2. Target **iOS 17+**, preferably an **arm64** device or Apple Silicon simulator (the on-device model ships an arm64 slice only).
3. Before using AI features like "Query", download the Gemma 4 E2B model under **Settings → AI** (~2.58 GB, Wi-Fi recommended, ≥6 GB free space required).
4. With no model present the app falls back to **rule-based** extraction (Vision OCR + regex) — it never becomes unusable due to a missing model.

---

## Visual Style

The app follows a **"paper medical notebook"** metaphor: warm beige paper background + white large-rounded cards + Songti serif headings + brick-red primary + low-saturation member color dots + purple AI accents + a floating glass TabBar. It deliberately avoids the cold "tech blue" to convey "family, care, verifiable." The full design system is in [Section 7](./doc/方案设计文档.md) of the spec.

---

## Known Limitations

- No dedicated test target.
- `MedicineBoxNotesApp.swift` is still a single ~4,900-line file; modularization is ongoing.
- CloudKit compatibility constraints: no `.unique` attributes, default `.nullify` relationships — deletions/changes require manual cascade cleanup.
- Local and iCloud are two independent containers; switching the backend only switches the view and **does not migrate data**.
- AI / OCR results may be incomplete — pages always keep original images and OCR text for verification.

---

> This README is an overview. Field names, structures, and implementation details are governed by the **code** and the spec at [`doc/方案设计文档.md`](./doc/方案设计文档.md) (Chinese).
