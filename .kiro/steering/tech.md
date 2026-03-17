# Tech Stack

## Mobile
- React Native + Expo (iOS + Android from one codebase)
- TypeScript (strict mode)
- NativeWind (latest stable) — Tailwind-style utility classes for React Native styling
- React Query (`@tanstack/react-query`) — server state, API calls, caching
- expo-secure-store — encrypted storage for auth tokens

## Backend
- Spring Boot 3.x, Java 21
- Stateless JWT auth (token exchange from Apple/Google Sign-In)
- Typed REST APIs with OpenAPI spec

## Database
- PostgreSQL — Neon or Supabase free tier

## Hosting
- Containerized backend on Cloud Run, Fly.io, or Render (free tier)

## Auth
- Sign in with Apple + Sign in with Google (mobile) → token exchange → backend-issued JWT
- No username/password

## Storage
- Local SQLite on device (expo-sqlite) for offline-first data
- Cloudflare R2 (free tier) for image/file storage if needed

## Offline
- Local SQLite as source of truth on device
- Sync service uses idempotent APIs; Offline_Queue drained in insertion order on reconnect

## Observability
- Micrometer → Grafana Cloud (free tier) for metrics
- Sentry for error tracking (mobile + backend)

## CI/CD
- GitHub Actions: build, test, container publish, deploy

## AI (Phase 2)
- On-device OCR via Google ML Kit
- Free-tier LLM (Groq or Hugging Face) called via a Spring Boot endpoint

## Charts
- Victory Native or react-native-chart-kit — bar and line charts only, max 3 data series

## Notifications
- Expo Notifications for scheduled reminders
- Max 3 push notifications per day; fall back to in-app banner if permission denied

## Testing
- Jest + React Native Testing Library for unit/integration tests
- Detox for E2E mobile tests
- JUnit 5 + Mockito for backend tests

## UI Component Library
- `src/ui/` — reusable primitives styled with NativeWind: `Button`, `Card`, `TextField`, `ListItem`, `Chip`, `Badge`, `Divider`, `EmptyState`, `Snackbar`
- All primitives accept `className` for NativeWind overrides; no inline styles anywhere
- Dark mode via `dark:` variants; system theme only (`darkMode: "media"` in tailwind config)

## Common Commands

```bash
# Install dependencies
npm install

# Install NativeWind and peer deps
npx expo install nativewind tailwindcss react-native-reanimated react-native-safe-area-context

# Run on iOS simulator
npx expo run:ios

# Run on Android emulator
npx expo run:android

# Run JS unit tests (single pass)
npx jest --runInBand

# Run E2E tests
npx detox test

# Type check
npx tsc --noEmit

# Lint
npx eslint . --ext .ts,.tsx

# Build backend
./mvnw clean package

# Run backend locally
./mvnw spring-boot:run

# Run backend tests
./mvnw test

# Build container image
docker build -t macroflow-api .
```
