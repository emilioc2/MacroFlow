# Best Practices

## TypeScript (Mobile)
- Strict mode always on — no `any`, no `@ts-ignore` without a comment explaining why
- Define all API response shapes in `src/types/`; never infer from raw fetch responses
- Use `unknown` over `any` when type is genuinely uncertain, then narrow explicitly

## React Native / Expo
- No inline styles — use NativeWind `className` for all styling; `StyleSheet.create` is not used
- Keep screens as thin orchestrators; extract logic into hooks and utils
- Never call SQLite or the API directly from a component — go through `db/` or `services/`
- Memoize expensive computations with `useMemo`; avoid re-renders from unstable references
- Always handle loading and error states in UI — no silent failures
- Minimum touch target 44×44 on all interactive elements
- Use `FlatList` with `keyExtractor` for all lists; memoize row components

## State Management (Zustand)
- One store per domain; keep stores small and focused
- Derive computed values (e.g. remaining macros) inside the store or a selector hook — not in render
- Never mutate state directly; always use the store's set function

## Offline / Sync
- All write operations go through the Offline_Queue — never write directly to the API while bypassing local SQLite
- Sync APIs must be idempotent; use client-generated UUIDs as idempotency keys
- Drain the queue in insertion order; log and skip (don't block) on unresolvable conflicts

## Backend (Spring Boot)
- Controllers must be thin — no business logic, only request validation and delegation to services
- Never expose JPA entities in API responses — always map to DTOs
- All DB schema changes via Flyway migrations; never use `spring.jpa.hibernate.ddl-auto=update` in production
- Validate all incoming DTOs with Bean Validation (`@Valid`, `@NotNull`, etc.)
- Return consistent error responses using a `@ControllerAdvice` exception handler
- JWT validation happens in the security filter — services assume an authenticated principal

## Security
- Never log JWTs, tokens, or any PII
- All secrets via environment variables — nothing hardcoded or committed
- Backend enforces ownership checks: a user can only read/write their own data
- TLS 1.2+ enforced in transit; AES-256 at rest for all user data

## API Design
- REST resources are nouns, plural (e.g. `/meal-entries`, `/foods`)
- Use standard HTTP status codes; 422 for validation errors, not 400
- Keep the OpenAPI spec (`openapi.yml`) in sync with every endpoint change
- Paginate list endpoints; never return unbounded collections

## Testing
- Unit test all `utils/` functions — they are pure and easy to test
- Mock the DB and API in component/hook tests; test behaviour not implementation
- Backend service layer tested with unit tests; controller layer with `@WebMvcTest`
- E2E (Detox) covers the critical happy paths: onboarding, log a meal, view summary

## Observability
- Use structured logging (JSON) in the backend — no `System.out.println`
- Attach a `userId` (hashed/opaque) to log context for traceability without PII
- Track errors in Sentry on both mobile and backend; include breadcrumbs, not raw data
