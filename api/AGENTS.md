# AGENTS.md

## Status

This workspace currently has no source files, manifests, git history, build/test config, or CI — commands and conventions below cannot yet be verified against code.

## Architecture (confirmed by owner)

This project is a **read-only content API** plus a **separate admin panel UI** used to add/manage content.

- The public API serves reads only. Do **not** add write/mutation endpoints (create/update/delete) to it.
- All content mutations happen through the admin panel UI (or its backing services), not the read API.
- Expect two surfaces with distinct entrypoints: the API and the admin UI. Treat them as separate apps/packages when code lands.
- The API is a **feed consumed by an app**: response payloads must match a fixed JSON structure for the origin data. The contract is dictated by the consuming app — do not redesign response shapes casually; any schema change must stay compatible with what the app expects. Content created via the admin panel must conform to this same structure.
- The app is still in progress; this structure is the current contract (confirmed by owner). Keep field names in Spanish exactly as listed — do not translate, rename, or add fields without confirmation.
- The feed exists so the app can **fill its local DB and apply updates** from it. The JSON is intentionally normalized like relational tables (with FK fields) — keep it that way; do not flatten, nest, or merge collections.

## Feed JSON contract (fixed)

All lists are keyed by string `id`. Types: `id`/text fields are strings, `pasos.orden` is an integer, `delegaciones.latitud`/`longitud` are numbers, booleans where noted.

- `categorias[]`: `id`, `nombre`, `icono`
- `tramites[]`: `id`, `categoriaId` (FK → categorias), `titulo`, `entidad`, `descripcion`, `costo`, `tiempoEstimado`, `icono`, `isQuickAccess` (optional boolean)
- `requisitos[]`: `id`, `tramiteId` (FK → tramites), `descripcion`, `esTramitePrevio` (boolean), `nota` (optional)
- `pasos[]`: `id`, `tramiteId` (FK → tramites), `orden` (integer), `descripcion`
- `delegaciones[]`: `id`, `nombreOficina`, `departamento`, `latitud` (number), `longitud` (number), `direccion`
- `delegacion_tramites[]`: `delegacionId` (FK → delegaciones), `tramiteId` (FK → tramites) — join table, no own `id`

### Version endpoint (confirmed by owner)

- `GET /api/feed/version` → `{ "version": "<16 hex chars>", "actualizadoEn": "<RFC3339>" }`
  - `version` = first 16 hex chars of SHA-256 over the canonical feed JSON; it changes only when feed content changes. `actualizadoEn` is when that content version was last observed to change.
  - The app should call this before re-downloading `/api/feed` and skip the sync when `version` matches its stored one.
  - Separate additive endpoint; the `/api/feed` payload shape above is unchanged. Read-only (GET/HEAD only, others 405). Response is `Cache-Control: no-store`.

## Once code lands

Revisit and replace unverified claims with repo-specific guidance:

- Exact commands for install, lint, typecheck, test (including single-test/single-package runs) and their required order.
- Package/workspace boundaries, real entrypoints for API and admin UI, and codegen/migration/build artifacts.
- Non-obvious env loading, dev servers, fixtures, and test prerequisites.

Only keep claims that can be verified against config or scripts; trust executable sources over prose.
