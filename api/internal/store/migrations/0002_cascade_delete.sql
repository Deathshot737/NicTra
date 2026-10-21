-- Deleting a categoria should cascade to its tramites (and, through the
-- existing cascades, to requisitos, pasos and delegacion_tramites).
ALTER TABLE tramites
  DROP CONSTRAINT IF EXISTS tramites_categoria_id_fkey;

ALTER TABLE tramites
  ADD CONSTRAINT tramites_categoria_id_fkey
  FOREIGN KEY (categoria_id) REFERENCES categorias(id) ON DELETE CASCADE;
