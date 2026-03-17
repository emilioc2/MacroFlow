# Project Structure

## Mobile (React Native + Expo)

```
src/
  components/       # Shared, reusable UI components
  screens/          # One file per screen (HomeScreen, AddMealScreen, etc.)
  navigation/       # React Navigation stack and tab definitions
  store/            # Zustand stores — one per domain (mealStore, userStore, etc.)
  hooks/            # Custom React hooks
  services/         # API clients (typed, OpenAPI-generated or hand-written), auth, notifications
  db/               # expo-sqlite schema, migrations, and query helpers
  sync/             # Offline_Queue logic and sync orchestration
  utils/            # Pure utility functions (TDEE calc, macro math, etc.)
  types/            # Shared TypeScript types and interfaces
  constants/        # App-wide constants (macro ratios, calorie coefficients, etc.)
  ui/               # NativeWind-styled primitives: Button, Card, TextField, ListItem, etc.

assets/             # Images, fonts, icons
__tests__/          # Jest unit and integration tests (mirrors src/ structure)
e2e/                # Detox E2E test specs
tailwind.config.js  # NativeWind token set (colors, spacing, typography)
```

## Backend (Spring Boot)

```
src/main/java/com/macroflow/
  controller/       # REST controllers (one per resource)
  service/          # Business logic layer
  repository/       # Spring Data JPA repositories
  model/            # JPA entities
  dto/              # Request/response DTOs
  security/         # JWT filter, auth config
  config/           # Spring config classes (OpenAPI, CORS, etc.)

src/main/resources/
  db/migration/     # Flyway migrations (V1__init.sql, etc.)
  application.yml   # App config

src/test/           # JUnit 5 + Mockito tests (mirrors main structure)
Dockerfile
```

## Conventions

### Mobile
- One component or screen per file; filename matches the exported name (PascalCase)
- Hooks prefixed with `use` (e.g., `useDailyLog`, `useOfflineQueue`)
- Zustand stores named by domain; no Redux
- All business logic (TDEE, calorie formula, macro splits) lives in `utils/` — not in components
- SQLite access only through `db/` helpers; no raw SQL in components or screens
- Sync logic (Offline_Queue, conflict resolution) isolated in `sync/`
- Types shared across layers go in `types/`; local types stay co-located
- No barrel `index.ts` re-exports unless the folder has 3+ public exports
- All styling via NativeWind `className` — no `StyleSheet.create`, no inline styles
- Reusable UI primitives live in `src/ui/`; screens compose from these primitives

### Backend
- Controllers are thin — delegate to service layer
- DTOs for all API boundaries; never expose JPA entities directly
- All DB migrations via Flyway; no schema changes outside migration files
- OpenAPI spec kept up to date; used as contract for mobile API client

## Key Domain Rules

- Calorie formula: `(protein × 4) + (carbs × 4) + (fat × 9)`
- TDEE: Mifflin-St Jeor equation; adjust by −500 / 0 / +300 kcal for loss/maintain/gain
- Default macro split: 30% protein, 40% carbs, 30% fat of total calories
- Offline_Queue must be drained in insertion order on reconnect
- Sync APIs must be idempotent (safe to replay on retry)
- Summary_Card and Daily_Log totals must update within 1 second of any entry change
