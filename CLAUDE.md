# pppp (Pibs&PibettesPlayPokemon)

Design doc: https://claude.ai/artifact/58m66h93Jn9y9LyDrqKDrm (read with the Claude Docs tools, not web fetch).

## Backend (`backend/`)
- Spring Boot 3.5, Java 21, Maven multi-module, MVC layers. Package root `dev.pppp`.
  - `pppp-emulator`: `dev.pppp.emulator` — Coffee GB on its own thread. Plain Java, no Spring.
  - `pppp-core`: `model`, `exception`, `service`, `repository`, `job`, `config` — business logic. No web dependencies; Redis access only in `repository`.
  - `pppp-app`: `controller` (+ `ApiExceptionHandler`), `security`, `realtime` (SSE, screen WebSocket), `config`, `PpppApplication`.
  - Dependencies point one way: app -> core -> emulator.
- Build/test: `cd backend && mvn -q test`. Run: `mvn -q package -DskipTests && java -jar pppp-app/target/pppp-app-*.jar` with the repo-root `.env` exported (`set -a; . ../.env; set +a`). Integration tests live in pppp-app: `@SpringBootTest @DirtiesContext @AutoConfigureMockMvc @Import(TestRedis.class)` (Testcontainers Redis; Docker required). `@DirtiesContext` gives each class its own context and Redis: shared contexts leak settings, rate limits and mode between classes.
- Auth in tests: `.with(user("x@y.z").roles("PLAYER"))` or `oidcLogin()`; mutating calls need `.with(csrf())`.
- Emulator: Coffee GB, `eu.rekawek.coffeegb:core` from Maven Central.

## Conventions
- User id = lower-cased email: `CurrentUser.id(authentication)`.
- Errors: services throw domain exceptions, `ApiExceptionHandler` alone maps them to ProblemDetail: IllegalArgumentException 400, ForbiddenException 403, NotFoundException 404, IllegalStateException 409, RateLimitedException 429, QueueFullException 503 + Retry-After.
- Live events to browsers: services publish `new LiveEvent(type, payload)` via `ApplicationEventPublisher`; `LiveEventRelay` forwards to SSE (`/api/v1/events`).
- Settings: `SettingsService` (Redis JSON, publishes the new record as an ApplicationEvent on save). Admin GET/PUT under `/api/v1/admin/settings/{input,rate-limit,autosave}`.
- Admin actions announce themselves via `ChatService.postSystem(text)`.
- Redis keys follow the design doc's key layout table exactly.
- Keep it small: no interfaces with one implementation, no speculative config.

## Frontend (`frontend/`)
- SvelteKit 3 + Svelte 5 runes, TypeScript, Vite 8, adapter-static (SPA, `ssr = false`), served by nginx. Config lives in `vite.config.ts` (no svelte.config.js).
- Commands: `cd frontend && npm run check && npm test && npm run build`. Dev: `npm run dev` proxies /api, /ws, /oauth2, /login/oauth2 to localhost:8080.
- Screen designs to match for /play: `frontend/design/Play*.dc.html`. The sign-in page (`/`) is the handheld boot-up. Tokens in `src/app.css` have dark and light values (theme: `src/lib/theme.svelte.ts`, Auto/Light/Dark); use tokens, not hex, for anything that sits on the page background.
- Backend calls only through `src/lib/api.ts` (`api(method, path, body)`, adds X-XSRF-TOKEN, throws `ApiError{status, detail, retryAfter}`).
- Live events only through `src/lib/events.ts` (`on(type, handler)` returns unsubscribe; one shared EventSource).
- Imports via `#lib/*` need the emitted extension: `#lib/session.svelte.js`, `#lib/api.js`.
- Session: `src/lib/session.svelte.ts` (`session.me`, `loadMe()`, `isAdmin()`, `logout()`).
- Backend payload shapes: read the controllers/records in `backend/src/main/java/dev/pppp` rather than guessing.
- Accessibility: real buttons/labels, visible focus, `prefers-reduced-motion` respected, chat rendered as text never HTML.
