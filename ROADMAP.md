# Scotty — Android Beam Revival Roadmap

**Project:** Scotty — tap-to-transfer file sharing using NFC + Google Nearby Connections
**Stack:** Kotlin · Jetpack Compose · Material 3 Expressive · Google Nearby API
**Package:** `org.localsend.localsend_app` → target rename: `app.scotty`
**Last updated:** 2026-03-16

---

## Legend
- ✅ Complete
- 🔄 In Progress
- ⬜ Planned
- ❌ Blocked

---

## Phase 1 — Foundation & Branding (Iterations 1–5)
> Core theme, branding, scaffold, and permissions flow

| # | Task | Status | Notes |
|---|------|--------|-------|
| 1.1 | Rename app to "Scotty" (strings, label, theme) | 🔄 | |
| 1.2 | Material 3 Expressive theme (spring animations, vibrant palette, M3E tokens) | 🔄 | |
| 1.3 | Edge-to-edge scaffold with proper insets | 🔄 | |
| 1.4 | Dynamic color + fallback palette (teal/cyan Scotty brand) | ⬜ | |
| 1.5 | Splash / launch screen with beam animation | ⬜ | |
| 1.6 | Runtime permissions request flow (NFC, Nearby, Storage) | ⬜ | |
| 1.7 | App icon (adaptive, foreground beam icon) | ⬜ | |

## Phase 2 — Send Screen Polish (Iterations 6–10)
> The primary beam/send flow — file selection → tap → transfer

| # | Task | Status | Notes |
|---|------|--------|-------|
| 2.1 | File picker card with M3E large FAB + spring press animation | ⬜ | |
| 2.2 | File list with swipe-to-remove + animated entry/exit | ⬜ | |
| 2.3 | NFC "Touch to Beam" hero card — expressive pulsing animation | ⬜ | |
| 2.4 | Beam success state with confetti/morphing animation | ⬜ | |
| 2.5 | Transfer progress with M3 LinearProgressIndicator + file name | ⬜ | |
| 2.6 | Error/retry state with actionable snackbar | ⬜ | |
| 2.7 | Empty state illustration (no files selected) | ⬜ | |

## Phase 3 — Receive Screen (Iterations 11–13)
> Passive receive mode — show device ready state

| # | Task | Status | Notes |
|---|------|--------|-------|
| 3.1 | Receive screen with animated "Ready to receive" indicator | ⬜ | |
| 3.2 | Incoming transfer accept/reject dialog | ⬜ | |
| 3.3 | Received files list with share/open actions | ⬜ | |
| 3.4 | Transfer history persistence (DataStore) | ⬜ | |

## Phase 4 — Settings Screen (Iterations 14–15)
> Device name, preferences, about

| # | Task | Status | Notes |
|---|------|--------|-------|
| 4.1 | Settings screen with M3 grouped lists | ⬜ | |
| 4.2 | Device alias editor | ⬜ | |
| 4.3 | Theme toggle (System/Light/Dark) | ⬜ | |
| 4.4 | About section with version, licenses link | ⬜ | |

## Phase 5 — Polish & Accessibility (Iterations 16–20)
> Animations, a11y, performance, final QA

| # | Task | Status | Notes |
|---|------|--------|-------|
| 5.1 | Spring animations on all state transitions (M3 Expressive motion) | ⬜ | |
| 5.2 | Shared element transitions between states | ⬜ | |
| 5.3 | Content descriptions for all interactive elements | ⬜ | |
| 5.4 | Dark theme audit and contrast check | ⬜ | |
| 5.5 | Large text / font scale testing | ⬜ | |
| 5.6 | Bottom navigation bar (Send / Receive / Settings) | ⬜ | |
| 5.7 | Regression test: build + install + full flow end-to-end | ⬜ | |
| 5.8 | ProGuard/R8 rules review | ⬜ | |

---

## Phase 6 — Bug Fixes & Technical Debt (Audit Findings)
> Issues found in 2026-03-16 source audit. Fix before real-device testing.

| # | Task | Status | Notes |
|---|------|--------|-------|
| 6.1 | Wire AppSettings to DataStore (alias/settings reset every launch) | ⬜ | DataStore dep exists but unused |
| 6.2 | Fix CoroutineScope leak in NfcReaderManager (unscoped IO scope) | ⬜ | Must tie to lifecycle |
| 6.3 | Emit `NfcBeamStatus.Ready` state (currently defined but never set) | ⬜ | Dead state in sealed class |
| 6.4 | Replace hardcoded `"Target Device"` with real discovered endpoint name | ⬜ | In MainViewModel Connecting state |
| 6.5 | Surface received file path to user (no UI for received files) | ⬜ | NearbyTransferService saves but UI ignores |
| 6.6 | Generate/persist device fingerprint (defaults to empty string) | ⬜ | AppSettings.fingerprint = "" |

## Phase 7 — Full Functionality (Next Steps — Post UI Polish)
> Backend integration, real device testing, release prep

| # | Task | Status | Notes |
|---|------|--------|-------|
| 7.1 | NearbyTransferService: test real P2P file transfer end-to-end | ⬜ | Requires 2 physical devices |
| 7.2 | NfcReaderManager: validate HCE + NDEF reader pairing | ⬜ | Requires NFC-capable devices |
| 7.3 | Multi-file batch transfer support | ⬜ | |
| 7.4 | Large file transfer (>100MB) with chunking | ⬜ | |
| 7.5 | Background receive service (foreground service) | ⬜ | |
| 7.6 | Package rename: `org.localsend.localsend_app` → `app.scotty` | ⬜ | |
| 7.7 | Play Store listing prep (screenshots, description, privacy policy) | ⬜ | |
| 7.8 | Signed release APK / AAB | ⬜ | |
| 7.9 | Crashlytics / error reporting integration | ⬜ | |
| 7.10 | Unit tests for ViewModel + NearbyTransferService | ⬜ | |
| 7.11 | Instrumented UI tests (Espresso/Compose test) | ⬜ | |
| 7.12 | Battery/performance profiling on real devices | ⬜ | |

---

## Completion Estimate

| Phase | Tasks | Complete | % |
|-------|-------|----------|---|
| Phase 1 — Foundation | 7 | 0 | 0% |
| Phase 2 — Send Screen | 7 | 0 | 0% |
| Phase 3 — Receive Screen | 4 | 0 | 0% |
| Phase 4 — Settings Screen | 4 | 0 | 0% |
| Phase 5 — Polish & A11y | 8 | 0 | 0% |
| Phase 6 — Bug Fixes | 6 | 0 | 0% |
| Phase 7 — Full Functionality | 12 | 0 | 0% |
| **Total** | **48** | **0** | **~0%** |

> UI/UX polish target (Phases 1–5): **0 / 30 tasks = 0%**
> Backend/functionality (Phases 6–7): **0 / 18 tasks = 0%**
> Overall project: **0 / 48 tasks = ~0%**

---

## Source Audit Summary (2026-03-16)
**Total lines:** 1,182 across 10 Kotlin files
**Backend readiness:** NearbyTransferService ~90%, NfcHceService ~95%, NfcReaderManager ~95%
**UI readiness:** SendScreen ~85%, No ReceiveScreen, No SettingsScreen, No NavigationBar
**Critical gap:** White-on-white UI on emulator (dynamic color picks neutral palette)
