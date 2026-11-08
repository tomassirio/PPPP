# pppp (Pibs&PibettesPlayPokemon)

Design doc: https://claude.ai/artifact/58m66h93Jn9y9LyDrqKDrm (read with the Claude Docs tools, not web fetch).

## Backend (`backend/`)
- Spring Boot 3.5, Java 21, Maven. Package root `dev.pppp`, organised by feature (see design doc "Project structure").
- Build/test: `cd backend && mvn -q test`. Integration tests: `@SpringBootTest @AutoConfigureMockMvc @Import(TestRedis.class)` (Testcontainers Redis; Docker required).
- Auth in tests: `.with(user("x@y.z").roles("PLAYER"))` or `oidcLogin()`; mutating calls need `.with(csrf())`.
- Emulator: Coffee GB, `eu.rekawek.coffeegb:core` from Maven Central.

## Conventions
- User id = lower-cased email: `CurrentUser.id(authentication)`.
- Errors: throw `ResponseStatusException` (400/429/503...); invalid console transitions throw `IllegalStateException`, mapped to 409 ProblemDetail by `ApiExceptionHandler`.
- Live events to browsers: `EventPublisher.publish(type, payload)` (SSE at `/api/v1/events`).
- Settings: `SettingsService` (Redis JSON, publishes the new record as an ApplicationEvent on save). Admin GET/PUT under `/api/v1/admin/settings/{input,rate-limit,autosave}`.
- Admin actions announce themselves via `ChatService.postSystem(text)`.
- Redis keys follow the design doc's key layout table exactly.
- Keep it small: no interfaces with one implementation, no speculative config.
