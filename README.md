# ATHENA — Offline AI Study Assistant

Android starter project for a local-first study assistant.

## Current milestone: Android foundation (v0.1.0)
Included:
- Kotlin Android application shell with a dark ATHENA interface.
- Local knowledge-profile names stored in app-private preferences.
- Create and delete profile actions.
- Android document picker entry point for PDF / DOCX / TXT selection.
- GitHub Actions workflow to compile and publish a debug APK artifact.

Not included yet (do not treat as working features):
- Local LLM inference or model download/installation.
- PDF/DOCX text extraction, OCR, embeddings, vector search or RAG.
- AI professor chat, citations, exam analytics, viva evaluation or study planner.
- YouTube transcript processing.
- Offline speech recognition, text-to-speech or wake-word foreground service.

## Build
GitHub Actions runs `gradle assembleDebug` on pushes to `main` and on manual workflow dispatch.
The APK is uploaded as the `ATHENA-debug-apk` workflow artifact.

## Device target
Designed as a starting point for Android 11+ devices. Actual compatibility and performance must be verified on the target OPPO F15. The AI model and supporting assets will be added only after benchmarking the device and storage budget.

## Privacy
No network permission or cloud AI API is declared in this starter manifest. Imported document content is not yet copied or processed; the picker currently only confirms selection.
