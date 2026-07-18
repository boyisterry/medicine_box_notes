# MedicineBoxNotes Android Design QA

## Scope

- Reference: the seven iOS screenshots supplied by the user.
- Implementation: native Android build captured from the connected ZTE_7530N at 720 × 1600.
- Compared flows: Home, Records, Record detail, Medicine box, Medicine editor, and Local query.
- The Settings module keeps the same shared card, typography, spacing, and navigation system and was regression-checked on-device.

## Evidence

Each comparison places the source on the left and the Android implementation on the right in one image:

- `doc/qa-home-comparison.png`
- `doc/qa-records-comparison.png`
- `doc/qa-record-detail-comparison.png`
- `doc/qa-medicine-editor-comparison.png`
- `doc/qa-query-comparison.png`
- `doc/qa-records-bottom-add-comparison.png`
- `doc/qa-medicines-bottom-add-comparison.png`
- `doc/qa-records-compact-top-comparison.png`
- `doc/qa-medicines-compact-top-comparison.png`
- `doc/query-processing-visible.png`
- `doc/query-auto-scroll-processing.png`
- `doc/query-fast-stream-final.png`

Standalone Android captures are stored beside them as `doc/final-*.png`.

## Findings and fixes

1. **P1 — cards with short/empty content collapsed to intrinsic width.** Fixed by making every lazy-list card consume the parent width. Verified on Home low-stock, Record detail diagnosis/prescription, editor, query, and Settings cards.
2. **P1 — query content extended beneath the floating navigation on the 720 × 1600 device.** Fixed by tightening only the query header, safety-copy typography, text-entry minimum height, and web-search row. The whole primary query flow is now visible at the validation viewport.
3. **P2 — system status icons had insufficient contrast.** Fixed with explicit light status/navigation bar appearance. Verified on Android 11.
4. **P2 — page transition residue was suspected.** Navigation transitions were made deterministic. The remaining fixed white handle at the far-left edge was confirmed as the ZTE system sidebar overlay, outside the app window.
5. **P2 — visual language differed from the source.** Updated the paper/surface colors, sans-serif Chinese hierarchy, 26 dp cards, pill headers, floating five-tab navigation, member dots, badges, steppers, and AI action rows.
6. **User-directed iteration — page-level add actions.** Replaced the Records text action and the Medicine-box top action with one shared 58 dp circular `+` control, anchored at the bottom center above the navigation dock. Added 104 dp list bottom insets so content never sits beneath the control. Both controls were tapped on-device and correctly opened their respective create flows.
7. **User-directed iteration — obsolete top whitespace.** Reduced the former top-action reservation from 128/134 dp to a normal 24 dp page inset. Records filters and medicine cards now enter the first viewport immediately, while the bottom `+` remains clear of both content and navigation.
8. **User-directed iteration — model reply presentation.** Suppressed Gemma's raw JSON generation chunks, added a 900 ms breathing-opacity animation across thinking/searching/organizing phrases, automatically scrolled the answer card into view, and simulated fast final streaming in two-character chunks at 16 ms intervals. Verified with the installed on-device model; no JSON was exposed.

## State notes

- The user database contains three family members, one sparse test record, and one manually created medicine, while the reference uses four members and richer sample records. Differences in names, counts, medication schedule, summaries, and prescription rows are data-state differences rather than layout substitutions.
- The Android implementation preserves scrolling where its shorter physical viewport cannot display as much content as the taller iOS reference.

## Verification

- `:app:assembleDebug` — passed
- `:app:lintDebug` — passed
- `:core-ai:testDebugUnitTest` — passed
- `:core-model:test` — passed
- Final APK installed successfully through the device package installer.
- Final APK SHA-256: `b2f9284292ad2484f4f03ae67af8999ad27d2692adbca6673d1e754e11da7c74`

## Result

Final result: **passed**
