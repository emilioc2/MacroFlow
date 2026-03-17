# Product: MacroFlow

MacroFlow is a minimal, fast mobile app for iOS and Android that tracks daily macronutrient (protein, carbs, fat) and calorie intake. The core design philosophy is **low friction, speed, and an uncluttered interface**.

## Key Principles
- Every interaction should be achievable in as few taps as possible
- No social feed, no community features — accountability is done via native share sheets
- Offline-first: the app must work fully without a network connection
- Theme follows the device system setting (light/dark); no manual toggle

## Core Capabilities (Phase 1)
- Daily macro/calorie logging with food database search, custom foods, and saved meals
- Onboarding with TDEE calculation (Mifflin-St Jeor) and auto-set macro targets
- Home screen Summary Card showing remaining macros/calories at a glance
- Smart reminders (max 3/day) with per-type enable/disable and time configuration
- Weekly/monthly analytics (bar and line charts only, max 3 data series)
- Weekly summary sharing via native share sheet
- Offline queue with automatic sync on reconnect
- Sign in with Apple / Sign in with Google — no username/password
- Cloud sync via a Sync_Service with TLS 1.2+ in transit and AES-256 at rest
