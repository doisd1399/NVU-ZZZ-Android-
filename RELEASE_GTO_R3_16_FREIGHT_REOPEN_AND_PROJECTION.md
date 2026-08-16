# GTO R3.16 — Freight reopen lifecycle + MediaProjection transition hardening

## Corrections

- Added explicit freight-list lifecycle: visible → closed → reopened.
- A list reopened after a failed selection creates a new NVU GTO trip session.
- Old OCR generation, selected freight, visual selection and session snapshot are invalidated before the new attempt.
- The current MediaProjection session is preserved during selection retry.
- MediaProjection permission/reauthorization is treated as a technical transition and cannot create/close a freight-list cycle.
- Projection restart clears only transient missing-list counters, not the logical trip session.
- Added guards against late OCR/selection data leaking across the new session.
- APK versionCode 34 / versionName 1.0.34.

## Expected flow

Failed first selection → list closes → user reopens list → new trip session → fresh OCR/visual cache → new selection.

Permission prompt → no trip reset → permission granted → projection resumes → existing logical state continues.
