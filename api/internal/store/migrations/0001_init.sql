-- Initial schema matching feed JSON contract
CREATE TABLE IF NOT EXISTS categorias (
  id TEXT PRIMARY KEY,
  nombre TEXT NOT NULL,
  icono TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS tramites (
  id TEXT PRIMARY KEY,
  categoria_id TEXT NOT NULL REFERENCES categorias(id) ON DELETE RESTRICT,
  titulo TEXT NOT NULL,
  entidad TEXT NOT NULL,
  descripcion TEXT NOT NULL,
  costo TEXT NOT NULL,
  tiempo_estimado TEXT NOT NULL,
  icono TEXT NOT NULL,
  is_quick_access BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_tramites_categoria ON tramites(categoria_id);

CREATE TABLE IF NOT EXISTS requisitos (
  id TEXT PRIMARY KEY,
  tramite_id TEXT NOT NULL REFERENCES tramites(id) ON DELETE CASCADE,
  descripcion TEXT NOT NULL,
  es_tramite_previo BOOLEAN NOT NULL DEFAULT FALSE,
  nota TEXT
);

CREATE INDEX IF NOT EXISTS idx_requisitos_tramite ON requisitos(tramite_id);

CREATE TABLE IF NOT EXISTS pasos (
  id TEXT PRIMARY KEY,
  tramite_id TEXT NOT NULL REFERENCES tramites(id) ON DELETE CASCADE,
  orden INTEGER NOT NULL,
  descripcion TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pasos_tramite ON pasos(tramite_id);
CREATE INDEX IF NOT EXISTS idx_pasos_tramite_orden ON pasos(tramite_id, orden);

CREATE TABLE IF NOT EXISTS delegaciones (
  id TEXT PRIMARY KEY,
  nombre_oficina TEXT NOT NULL,
  departamento TEXT NOT NULL,
  latitud DOUBLE PRECISION NOT NULL,
  longitud DOUBLE PRECISION NOT NULL,
  direccion TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS delegacion_tramites (
  delegacion_id TEXT NOT NULL REFERENCES delegaciones(id) ON DELETE CASCADE,
  tramite_id TEXT NOT NULL REFERENCES tramites(id) ON DELETE CASCADE,
  PRIMARY KEY (delegacion_id, tramite_id)
);

CREATE INDEX IF NOT EXISTS idx_dt_tramite ON delegacion_tramites(tramite_id);

CREATE TABLE IF NOT EXISTS schema_migrations (
  version TEXT PRIMARY KEY,
  applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
