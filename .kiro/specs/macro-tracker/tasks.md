# Implementation Tasks: MacroFlow — Macro Tracker

## How to read this file
- `- [ ]` not started  |  `- [-]` in progress  |  `- [x]` complete
- Tasks without `*` are required. Tasks marked `*` are optional enhancements.
- Sub-tasks must 1 all be complete before the parent task is marked complete.
- Test locally after each numbered task before moving to the next.

---

## Phase A — Backend Foundation

### Task 1: Spring Boot project scaffold
- [x] 1.1 Initialise Spring Boot 3.x project with Java 21 via Spring Initializr (Web, Security, JPA, Validation, Actuator, Flyway, PostgreSQL driver)
- [x] 1.2 Add `jqwik` dependency for property-based tests
- [x] 1.3 Configure `application.yml` with datasource, JPA, Flyway, and Actuator settings; all secrets via environment variables
- [x] 1.4 Add Dockerfile (multi-stage: build with Maven, run with JRE 21 slim)
- [x] 1.5 Add `.github/workflows/backend.yml` — build, test, Docker publish on push to `main`
- [x] 1.6 Verify `./mvnw clean package` and `./mvnw test` pass on a clean checkout

### Task 2: Database migrations (Flyway)
- [x] 2.1 Write `V1__init.sql` — create all tables: `app_user`, `user_profile`, `daily_targets`, `food`, `meal_entry`, `saved_meal`, `saved_meal_item`, `recently_logged`, `reminder_config`, `user_preferences`
- [x] 2.2 Add indexes: `idx_meal_entry_user_date` on `meal_entry(user_id, log_date)`; index on `food(owner_id)` and `food(external_id)`
- [x] 2.3 Verify Flyway applies migration cleanly against a local PostgreSQL instance (or Testcontainers)

### Task 3: JWT authentication
- [x] 3.1 Implement `POST /auth/token` — accept Apple or Google provider token, verify with provider, create or look up `app_user`, return signed access JWT (15 min) + opaque refresh token (30 days)
- [x] 3.2 Implement `POST /auth/refresh` — validate refresh token, rotate it, return new access JWT + new refresh token
- [x] 3.3 Implement `DELETE /auth/session` — revoke refresh token (called on sign-out)
- [x] 3.4 Implement `DELETE /auth/account` — schedule account + all associated data deletion within 30 days; return 202
- [x] 3.5 Add JWT security filter — validates `Authorization: Bearer` header on all protected routes; attaches principal to security context
- [x] 3.6 Add `@ControllerAdvice` global exception handler — consistent error envelope `{ status, error, message, fields }`
- [x] 3.7 Write `@WebMvcTest` controller tests: happy path, 401 for missing/invalid JWT, 422 for bad DTO

### Task 4: User profile and targets API
- [x] 4.1 Implement `GET /users/me` and `PUT /users/me` — profile fields: sex, date_of_birth, height_cm, weight_kg, activity, goal, timezone; ownership enforced
- [x] 4.2 Implement `GET /users/me/preferences` and `PUT /users/me/preferences` — recently_logged_max, tutorial_shown, theme
- [x] 4.3 Implement TDEE calculation utility (`MacroUtils.calculateTdee`) using Mifflin-St Jeor; adjust by −500/0/+300 for lose/maintain/gain; derive age from date_of_birth at call time
- [x] 4.4 Implement default macro split utility (`MacroUtils.calculateMacroSplit`) — 30% protein, 40% carbs, 30% fat of total calories
- [x] 4.5 Write `@Property(tries = 100)` jqwik tests for TDEE correctness (Property 8) and macro split correctness (Property 9)
- [x] 4.6 Write `@WebMvcTest` tests: 401 for unauthenticated, 403 for wrong user, 422 for invalid date_of_birth format

### Task 5: Food search API
- [x] 5.1 Implement `GET /foods?q=` — fan-out to Open Food Facts and USDA FoodData Central in parallel; normalise both to `FoodDTO` (id, name, calories, proteinG, carbsG, fatG, servingG, source, externalId); deduplicate by name+source (USDA wins for whole foods, OFF kept for branded)
- [x] 5.2 Store USDA API key in environment variable `USDA_API_KEY`; never hardcode
- [x] 5.3 Handle external API timeout/error gracefully — return partial results with `partial: true` flag; log error via Micrometer
- [x] 5.4 Implement `POST /foods/custom`, `PUT /foods/custom/{id}`, `DELETE /foods/custom/{id}` — ownership enforced; DELETE does not alter existing meal entries
- [x] 5.5 Write service unit tests: deduplication logic, partial result handling, ownership check on custom food mutations

### Task 6: Meal entries and saved meals API
- [x] 6.1 Implement `GET /meal-entries?startDate=&endDate=` — returns paginated entries for the authenticated user within the date range
- [x] 6.2 Implement `POST /sync/batch` — idempotent batch upsert; process operations in order; resolve conflicts by latest `client_ts`; return per-operation result; enforce 7-day edit window using user's stored timezone (return 422 for out-of-window mutations)
- [x] 6.3 Implement `GET /saved-meals`, `POST /saved-meals`, `PUT /saved-meals/{id}`, `DELETE /saved-meals/{id}` — ownership enforced; DELETE does not affect previously logged entries
- [x] 6.4 Write `@Property(tries = 100)` jqwik test for conflict resolution by latest timestamp (Property 26)
- [x] 6.5 Write `@WebMvcTest` tests: idempotent sync (duplicate client_id returns 200), 422 for out-of-window edit, 403 for wrong user

### Task 7: Analytics API
- [x] 7.1 Implement `GET /analytics/weekly` — average daily intake per macro and calories for the most recent 7-day period; adherence % per macro; streak count
- [x] 7.2 Implement `GET /analytics/monthly` — daily calorie series for the most recent 30 days
- [x] 7.3 Write `@Property(tries = 100)` jqwik tests for weekly average correctness (Property 13), daily calorie aggregation (Property 14), adherence % (Property 15)
- [ ] 7.4 Write Testcontainers integration test: insert 30 days of entries, verify monthly series length and values

### Task 8: Observability and error handling
- [x] 8.1 Configure Micrometer with Grafana Cloud exporter; expose `/actuator/health` and `/actuator/metrics`
- [x] 8.2 Add Sentry SDK for backend; attach opaque userId to error context (never raw PII)
- [x] 8.3 Configure structured JSON logging (Logback); no `System.out.println` anywhere
- [x] 8.4 Verify all error scenarios from the Error Handling table return the correct HTTP status and envelope

---

## Phase B — Mobile Foundation

### Task 9: Expo project scaffold
- [ ] 9.1 Initialise Expo project with TypeScript strict mode; configure `tsconfig.json` with `"strict": true`
- [ ] 9.2 Install and configure NativeWind: `tailwind.config.js` with full token set (colors, spacing, font sizes, border radius, `darkMode: "class"`), `babel.config.js` with `nativewind/babel`, `metro.config.js` with `withNativeWind`, `global.css` imported first in `App.tsx`
- [ ] 9.3 Install dependencies: `@tanstack/react-query`, `zustand`, `expo-secure-store`, `expo-sqlite`, `expo-notifications`, `axios`, `fast-check`
- [ ] 9.4 Set up React Navigation: bottom tab navigator (Home, Log, Analytics, Settings) + stack navigators per tab
- [ ] 9.5 Configure `QueryClient` with `staleTime: 5 * 60 * 1000` and `retry: 1`
- [ ] 9.6 Add `.github/workflows/mobile.yml` — type-check (`tsc --noEmit`), lint (`eslint`), Jest unit tests on push to `main`

### Task 10: SQLite schema and db helpers
- [ ] 10.1 Implement `src/db/schema.ts` — `initDb()` function that runs all `CREATE TABLE IF NOT EXISTS` statements for all 10 tables in the correct dependency order
- [ ] 10.2 Implement `src/db/mealEntryDb.ts` — `insertMealEntry`, `updateMealEntry`, `softDeleteMealEntry`, `getMealEntriesForDate`, `getMealEntriesForDateRange`; enforce 7-day edit window (reject mutations outside window)
- [ ] 10.3 Implement `src/db/foodDb.ts` — `upsertFood`, `searchFoodsLocal`, `getFoodById`; on-launch cleanup: hard-delete non-CUSTOM rows where food_id not in meal_entry or recently_logged and synced_at older than 90 days
- [ ] 10.4 Implement `src/db/recentlyLoggedDb.ts` — `upsertRecentlyLogged(userId, foodId)`, `getRecentlyLogged(userId, max)`; after every upsert prune rows beyond max for that user
- [ ] 10.5 Implement `src/db/offlineQueueDb.ts` — `enqueue`, `getPendingOps`, `markSent`, `markFailed`; on-launch cleanup: hard-delete sent rows older than 7 days and failed rows older than 30 days
- [ ] 10.6 Implement `src/db/userProfileDb.ts`, `src/db/dailyTargetsDb.ts`, `src/db/savedMealDb.ts`, `src/db/userPreferencesDb.ts`, `src/db/reminderConfigDb.ts` — CRUD helpers for remaining tables
- [ ] 10.7 Write Jest unit tests for all db helpers using an in-memory SQLite instance; test 7-day window enforcement, cleanup policies, recently_logged pruning

### Task 11: Zustand stores
- [ ] 11.1 Implement `src/store/mealStore.ts` — `entries`, `addEntry`, `editEntry`, `deleteEntry`, `duplicateEntry`, `loadDay`; derived `totals` and `remaining`; all mutations write to SQLite first then enqueue to offline_queue
- [ ] 11.2 Implement `src/store/userStore.ts` — `profile`, `targets`, `theme`; `setProfile`, `overrideTargets`, `recalculateTargets`, `setTheme`; `recalculateTargets` calls TDEE util and macro split util; `setTheme` writes to SQLite and enqueues sync
- [ ] 11.3 Implement `src/store/reminderStore.ts` — `config`, `setConfig`, `scheduleAll`, `cancelAll`; `scheduleAll` schedules daily reminders via Expo Notifications repeating trigger; weekly check-in uses weekly trigger (separate scheduling path)
- [ ] 11.4 Implement `src/store/analyticsStore.ts` — `weeklyAverages`, `monthlyCalories`, `adherence`, `streakCount`; `loadWeekly`, `loadMonthly`
- [ ] 11.5 Write Jest unit tests for all store actions; mock `db/` layer; verify state transitions and derived values

### Task 12: Utility functions
- [ ] 12.1 Implement `src/utils/macroUtils.ts` — `calculateCalories(protein, carbs, fat)`, `calculateTdee(profile)` (Mifflin-St Jeor, age derived from dateOfBirth), `calculateMacroSplit(calories)`, `scalePortionMacros(food, multiplier)`, `convertServingsToGrams(servings, servingG)`, `convertGramsToServings(grams, servingG)`
- [ ] 12.2 Implement `src/utils/analyticsUtils.ts` — `computeWeeklyAverages(entries, targets)`, `computeAdherence(actual, target)`, `computeStreak(entries)`
- [ ] 12.3 Implement `src/utils/dateUtils.ts` — `isWithin7DayWindow(logDate, timezone)`, `toLocalDate(isoString, timezone)`, `formatDisplayDate(isoString)`
- [ ] 12.4 Write Jest unit tests for all utils (example-based); write `fast-check` property-based tests for Properties 1, 8, 9, 13, 14, 15, 20, 21 (minimum 100 runs each)

### Task 13: Services and API client
- [ ] 13.1 Implement `src/services/apiClient.ts` — Axios instance with base URL from env; request interceptor attaches `Authorization: Bearer <accessToken>` from `userStore`; response interceptor handles 401 by calling silent refresh, then retrying the original request once; if refresh fails, clears tokens and redirects to sign-in
- [ ] 13.2 Implement `src/services/authService.ts` — `exchangeToken(provider, providerToken)`, `refreshAccessToken()`, `revokeSession()`, `deleteAccount()`; refresh token stored/read via `expo-secure-store`; access token held in `userStore` memory only
- [ ] 13.3 Implement `src/services/syncService.ts` — `syncBatch(ops: OfflineOperation[])` posts chunks of 50 ops to `POST /sync/batch`; returns per-op results
- [ ] 13.4 Implement `src/services/foodService.ts` — `searchFoods(query)` calls `GET /foods?q=`; results written to SQLite via `foodDb.upsertFood`
- [ ] 13.5 Write Jest unit tests for `syncService`: verify 120 ops are split into 3 requests of 50, 50, 20

### Task 14: Sync orchestration
- [ ] 14.1 Implement `src/sync/syncOrchestrator.ts` — `drainQueue()`: reads pending ops from `offlineQueueDb`, calls `syncService.syncBatch` in chunks of 50, marks each chunk complete before sending the next; resumes from first incomplete chunk on reconnect
- [ ] 14.2 Implement network listener in `App.tsx` using `@react-native-community/netinfo` — calls `drainQueue()` on reconnect
- [ ] 14.3 Write Jest unit tests: mock network; verify queue drains in insertion order; verify mid-drain resume; verify already-sent ops are not re-sent

### Task 15: `src/ui/` component library
- [ ] 15.1 Implement `Button.tsx` — `variant: primary | ghost`; `disabled` prop; `accessibilityRole="button"`, `accessibilityLabel`; min touch target 44×44
- [ ] 15.2 Implement `Card.tsx` — surface bg, rounded-md, border; accepts `children` and `className`
- [ ] 15.3 Implement `TextField.tsx` — label + `TextInput` + inline error message; `accessibilityLabel` matching visible label
- [ ] 15.4 Implement `ListItem.tsx` — left label, right value, optional chevron, optional `onPress`
- [ ] 15.5 Implement `Chip.tsx` — pill shape; `selected` prop toggles between unselected and selected class strings; `accessibilityRole="button"`
- [ ] 15.6 Implement `Badge.tsx`, `Divider.tsx`, `EmptyState.tsx`, `Snackbar.tsx`
- [ ] 15.7 Write Jest snapshot tests and interaction tests for all primitives; verify dark mode class application

---

## Phase C — Mobile Screens

### Task 16: Onboarding and authentication screens
- [ ] 16.1 Implement `SignInScreen` — Sign in with Apple button, Sign in with Google button; calls `authService.exchangeToken`; on success navigates to onboarding or home
- [ ] 16.2 Implement `OnboardingScreen` (multi-step): step 1 collects sex, date of birth (date picker), height, weight, activity level, timezone; step 2 collects goal; step 3 shows calculated targets with "Looks good" and "Adjust manually" options
- [ ] 16.3 Implement `TutorialScreen` — 4 skippable cards; on complete or skip sets `tutorialShown = true` in `userPreferencesDb` and enqueues sync; navigates to home
- [ ] 16.4 Implement `ThemeProvider` in `App.tsx` — reads `userStore.theme`; applies `dark` class to root view when theme is `dark` or when theme is `system` and `useColorScheme()` returns `dark`; reacts to store changes within 1 second
- [ ] 16.5 Write Jest tests: onboarding step navigation, skip tutorial sets flag, theme class applied correctly for all three theme values

### Task 17: Home screen
- [ ] 17.1 Implement `SummaryCard` component — displays remaining calories and 3 `MacroProgressBar` components; reads from `mealStore.remaining` and `userStore.targets`; updates within 1 second of any entry change
- [ ] 17.2 Implement `MacroProgressBar` component — props: `label`, `consumed`, `target`, `className?`; renders warning color when `consumed >= target`
- [ ] 17.3 Implement `MealEntryRow` component — swipe-to-delete/edit (left swipe reveals Edit and Delete actions); long-press context menu with Edit, Duplicate, Delete; delete shows `Snackbar` undo for 5 seconds
- [ ] 17.4 Implement `HomeScreen` — `SummaryCard` at top; `FlatList` of `MealEntryRow` for today's entries; Quick-add FAB navigates to `AddMealScreen`; calls `mealStore.loadDay` on mount
- [ ] 17.5 Write Jest tests: SummaryCard shows over-target warning when consumed >= target; MealEntryRow delete shows undo snackbar; HomeScreen renders entries from store
- [ ] 17.6 Write `fast-check` property test for Property 22 (summary card remaining values) and Property 23 (over-target indicator)

### Task 18: Add meal and food detail screens
- [ ] 18.1 Implement `useFoodSearch(query)` hook — debounce 300ms; disabled when query < 2 chars; shows SQLite cache results immediately; shows in-field loading indicator while API request is in-flight; merges API results on arrival
- [ ] 18.2 Implement `RecentlyLoggedList` component — reads from `recentlyLoggedDb.getRecentlyLogged(userId, max)`; taps navigate to `FoodDetailScreen`
- [ ] 18.3 Implement `AddMealScreen` — manual macro entry fields (primary, top); `RecentlyLoggedList` (middle); food search field (bottom); date selector for rolling 7-day window; saved meals picker
- [ ] 18.4 Implement `FoodDetailScreen` — `PortionPicker` with 0.5×/1×/1.5×/2× `Chip` quick-select and gram/serving `TextField`; live macro preview; "Add to Log" calls `mealStore.addEntry`
- [ ] 18.5 Implement `PortionPicker` component — `Chip` row + `TextField` numeric input + unit toggle (servings/grams); unit switch converts current value without requiring re-entry
- [ ] 18.6 Implement custom food form — name, serving size, protein/carbs/fat inputs; calories auto-calculated; save calls `foodDb.upsertFood` and enqueues sync
- [ ] 18.7 Write Jest tests: debounce fires after 300ms not before; unit toggle converts value correctly; date selector rejects dates outside 7-day window
- [ ] 18.8 Write `fast-check` property tests for Property 20 (portion multiplier scaling) and Property 21 (serving/gram round-trip)

### Task 19: Analytics screen
- [ ] 19.1 Implement `ChartWrapper` component — thin wrapper around Victory Native; enforces max 3 data series; bar chart for weekly view, line chart for monthly view
- [ ] 19.2 Implement `AnalyticsScreen` — week/month `Chip` toggle; weekly bar chart (avg macros vs target); adherence % row; monthly line chart (daily calories 30d); day drill-down on tap; partial data message when fewer than 7 days
- [ ] 19.3 Implement `useWeeklyAnalytics()` and `useMonthlyAnalytics()` React Query hooks
- [ ] 19.4 Implement weekly summary share — generates text/image summary (avg macros, adherence %, streak count); invokes native share sheet via `Share.share()`
- [ ] 19.5 Write Jest tests: partial data message shown when < 7 days; share generates correct content; adherence % computed correctly

### Task 20: Settings screen
- [ ] 20.1 Implement `SettingsScreen` — list items: Profile, Daily Targets, Reminders, Recently Logged (max items), Account, Replay Tutorial, App version
- [ ] 20.2 Implement `ProfileScreen` — editable fields: sex, date of birth, height, weight, activity, goal, timezone; save calls `userStore.setProfile` which triggers `recalculateTargets`
- [ ] 20.3 Implement `DailyTargetsScreen` — shows calculated targets; allows per-field override; "Reset to calculated" clears overrides; save calls `userStore.overrideTargets`
- [ ] 20.4 Implement `RemindersScreen` — toggle + time picker for meal, end-of-day, streak reminders; toggle + day picker for weekly check-in; calls `reminderStore.setConfig` then `reminderStore.scheduleAll`
- [ ] 20.5 Implement `AccountScreen` — sign out (clears tokens, retains local SQLite data); delete account (confirmation dialog, calls `authService.deleteAccount`)
- [ ] 20.6 Implement `ThemeSelector` component — three `Chip` options (Light / Dark / System); on select calls `userStore.setTheme`; rendered in `SettingsScreen`
- [ ] 20.7 Write Jest tests: profile save triggers target recalculation; theme selector writes correct value to store; sign-out clears tokens but not SQLite

---

## Phase D — Integration and Quality

### Task 21: Property-based test suite
- [ ] 21.1 Implement all 31 `fast-check` property tests in `__tests__/properties/` — one test per property, tagged `Feature: macro-tracker, Property N: <text>`, minimum 100 runs each
- [ ] 21.2 Implement all backend `@Property` jqwik tests for Properties 1, 8, 9, 13, 14, 15, 26 — minimum 100 tries each
- [ ] 21.3 Verify all property tests pass with `npx jest --runInBand` (mobile) and `./mvnw test` (backend)

### Task 22: E2E tests (Detox)
- [ ] 22.1 Write E2E test: onboarding flow → home screen shows correct targets
- [ ] 22.2 Write E2E test: log a meal via search → summary card updates within 1 second
- [ ] 22.3 Write E2E test: log a meal offline → reconnect → data synced to backend
- [ ] 22.4 Write E2E test: edit and delete a meal entry → totals update within 1 second
- [ ] 22.5 Write E2E test: view analytics after 7 days of logging

### Task 23: Offline and sync integration tests
- [ ] 23.1 Write Jest integration test: 120 queued ops drain as 3 batch requests of 50, 50, 20 in insertion order
- [ ] 23.2 Write Jest integration test: mid-drain network drop → reconnect resumes from first incomplete chunk; already-sent ops not re-sent
- [ ] 23.3 Write Testcontainers backend integration test: full sync round-trip — write offline, reconnect, verify server state matches local SQLite
- [ ] 23.4 Write Testcontainers backend integration test: account deletion removes all rows across all tables

### Task 24: EAS Build and distribution
- [ ] 24.1 Install EAS CLI and run `eas init` to link the project to an Expo account
- [ ] 24.2 Configure `eas.json` with three build profiles: `development` (internal distribution, debug), `preview` (internal distribution, release), `production` (store distribution, release)
- [ ] 24.3 Configure iOS build: set bundle identifier, provisioning profile, and App Store Connect API key in EAS secrets
- [ ] 24.4 Configure Android build: set application ID and upload keystore in EAS secrets
- [ ] 24.5 Add `eas build` step to `.github/workflows/mobile.yml` — trigger `preview` build on push to `main`; trigger `production` build on version tag push
- [ ] 24.6 Document TestFlight submission steps (iOS) and APK sideload steps (Android) in `README.md`
