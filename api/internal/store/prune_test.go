package store

import (
	"context"
	"testing"
	"testing/fstest"
)

func TestPruneRemovesRowsNotInSeed(t *testing.T) {
	ctx := context.Background()
	s := NewMemory()
	// Seed-like rows plus one stray row in every table.
	s.Categorias().Save(ctx, Categoria{ID: "cat_keep", Nombre: "k", Icono: "x"})
	s.Categorias().Save(ctx, Categoria{ID: "cat_stray", Nombre: "s", Icono: "x"})
	s.Tramites().Save(ctx, Tramite{ID: "t_keep", CategoriaID: "cat_keep"})
	s.Tramites().Save(ctx, Tramite{ID: "t_stray", CategoriaID: "cat_stray"})
	s.Requisitos().Save(ctx, Requisito{ID: "r_keep", TramiteID: "t_keep"})
	s.Requisitos().Save(ctx, Requisito{ID: "r_stray", TramiteID: "t_stray"})
	s.Pasos().Save(ctx, Paso{ID: "p_keep", TramiteID: "t_keep"})
	s.Pasos().Save(ctx, Paso{ID: "p_stray", TramiteID: "t_stray"})
	s.Delegaciones().Save(ctx, Delegacion{ID: "d_keep"})
	s.Delegaciones().Save(ctx, Delegacion{ID: "d_stray"})
	s.DelegacionTramites().Add(ctx, DelegacionTramite{DelegacionID: "d_keep", TramiteID: "t_keep"})
	s.DelegacionTramites().Add(ctx, DelegacionTramite{DelegacionID: "d_stray", TramiteID: "t_stray"})

	seedFS := fstest.MapFS{
		"seed.json": &fstest.MapFile{Data: []byte(`{
			"categorias":[{"id":"cat_keep","nombre":"k","icono":"x"}],
			"tramites":[{"id":"t_keep","categoriaId":"cat_keep","titulo":"t","entidad":"e","descripcion":"d","costo":"c","tiempoEstimado":"te","icono":"i"}],
			"requisitos":[{"id":"r_keep","tramiteId":"t_keep","descripcion":"d","esTramitePrevio":false}],
			"pasos":[{"id":"p_keep","tramiteId":"t_keep","orden":1,"descripcion":"d"}],
			"delegaciones":[{"id":"d_keep","nombreOficina":"o","departamento":"dep","latitud":1,"longitud":2,"direccion":"dir"}],
			"delegacion_tramites":[{"delegacionId":"d_keep","tramiteId":"t_keep"}]
		}`)},
	}

	if err := PruneFromFS(ctx, s, seedFS); err != nil {
		t.Fatalf("PruneFromFS: %v", err)
	}

	assertGone := func(kind string, gotErr error) {
		t.Helper()
		if gotErr != ErrNotFound {
			t.Errorf("%s: still present (err=%v), want ErrNotFound", kind, gotErr)
		}
	}
	_, err := s.Categorias().Get(ctx, "cat_stray")
	assertGone("categoria stray", err)
	_, err = s.Tramites().Get(ctx, "t_stray")
	assertGone("tramite stray", err)
	_, err = s.Requisitos().Get(ctx, "r_stray")
	assertGone("requisito stray", err)
	_, err = s.Pasos().Get(ctx, "p_stray")
	assertGone("paso stray", err)
	_, err = s.Delegaciones().Get(ctx, "d_stray")
	assertGone("delegacion stray", err)

	joins, _ := s.DelegacionTramites().List(ctx)
	if len(joins) != 1 || joins[0].DelegacionID != "d_keep" {
		t.Errorf("join after prune = %+v, want only d_keep->t_keep", joins)
	}

	if _, err := s.Categorias().Get(ctx, "cat_keep"); err != nil {
		t.Errorf("categoria keep removed: %v", err)
	}
	if _, err := s.Tramites().Get(ctx, "t_keep"); err != nil {
		t.Errorf("tramite keep removed: %v", err)
	}
}
