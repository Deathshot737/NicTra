package store

import (
	"context"
	"encoding/json"
	"io/fs"
	"testing"
	"testing/fstest"

	data "contentapi/internal/Data"
)

// jsonCounts parses the embedded seed files to learn the expected row counts.
func jsonCounts(t *testing.T) map[string]int {
	t.Helper()
	counts := map[string]int{}
	entries, err := fs.ReadDir(data.FS, ".")
	if err != nil {
		t.Fatalf("read embedded data: %v", err)
	}
	for _, e := range entries {
		b, err := fs.ReadFile(data.FS, e.Name())
		if err != nil {
			t.Fatal(err)
		}
		var f Feed
		if err := json.Unmarshal(b, &f); err != nil {
			t.Fatalf("parse %s: %v", e.Name(), err)
		}
		counts["categorias"] += len(f.Categorias)
		counts["tramites"] += len(f.Tramites)
		counts["requisitos"] += len(f.Requisitos)
		counts["pasos"] += len(f.Pasos)
		counts["delegaciones"] += len(f.Delegaciones)
		counts["delegacion_tramites"] += len(f.DelegacionTramites)
	}
	return counts
}

func TestSeedLoadsAllEmbeddedData(t *testing.T) {
	ctx := context.Background()
	s := NewMemory()
	if err := Seed(ctx, s); err != nil {
		t.Fatalf("Seed: %v", err)
	}

	want := jsonCounts(t)
	for kind, n := range want {
		if n == 0 {
			t.Fatalf("seed file defines no %s", kind)
		}
	}

	got := map[string]int{}
	cats, err := s.Categorias().List(ctx)
	if err != nil {
		t.Fatal(err)
	}
	got["categorias"] = len(cats)
	trams, err := s.Tramites().List(ctx)
	if err != nil {
		t.Fatal(err)
	}
	got["tramites"] = len(trams)
	reqs, err := s.Requisitos().List(ctx)
	if err != nil {
		t.Fatal(err)
	}
	got["requisitos"] = len(reqs)
	pasos, err := s.Pasos().List(ctx)
	if err != nil {
		t.Fatal(err)
	}
	got["pasos"] = len(pasos)
	dels, err := s.Delegaciones().List(ctx)
	if err != nil {
		t.Fatal(err)
	}
	got["delegaciones"] = len(dels)
	joins, err := s.DelegacionTramites().List(ctx)
	if err != nil {
		t.Fatal(err)
	}
	got["delegacion_tramites"] = len(joins)

	for kind, n := range want {
		if got[kind] != n {
			t.Errorf("store %s = %d, seed file has %d", kind, got[kind], n)
		}
	}
}

func TestSeedFKIntegrity(t *testing.T) {
	ctx := context.Background()
	s := NewMemory()
	if err := Seed(ctx, s); err != nil {
		t.Fatalf("Seed: %v", err)
	}

	catIDs := map[string]bool{}
	cats, _ := s.Categorias().List(ctx)
	for _, c := range cats {
		catIDs[c.ID] = true
	}
	tramIDs := map[string]bool{}
	trams, _ := s.Tramites().List(ctx)
	for _, tr := range trams {
		tramIDs[tr.ID] = true
		if !catIDs[tr.CategoriaID] {
			t.Errorf("tramite %q: unknown categoria %q", tr.ID, tr.CategoriaID)
		}
	}
	reqs, _ := s.Requisitos().List(ctx)
	for _, r := range reqs {
		if !tramIDs[r.TramiteID] {
			t.Errorf("requisito %q: unknown tramite %q", r.ID, r.TramiteID)
		}
	}
	ps, _ := s.Pasos().List(ctx)
	for _, p := range ps {
		if !tramIDs[p.TramiteID] {
			t.Errorf("paso %q: unknown tramite %q", p.ID, p.TramiteID)
		}
	}
	delIDs := map[string]bool{}
	dels, _ := s.Delegaciones().List(ctx)
	for _, d := range dels {
		delIDs[d.ID] = true
	}
	joins, _ := s.DelegacionTramites().List(ctx)
	for _, j := range joins {
		if !delIDs[j.DelegacionID] {
			t.Errorf("join: unknown delegacion %q", j.DelegacionID)
		}
		if !tramIDs[j.TramiteID] {
			t.Errorf("join: unknown tramite %q", j.TramiteID)
		}
	}
}

func TestSeedIdempotent(t *testing.T) {
	ctx := context.Background()
	s := NewMemory()
	if err := Seed(ctx, s); err != nil {
		t.Fatalf("Seed #1: %v", err)
	}
	if err := Seed(ctx, s); err != nil {
		t.Fatalf("Seed #2: %v", err)
	}
	want := jsonCounts(t)
	trams, _ := s.Tramites().List(ctx)
	if len(trams) != want["tramites"] {
		t.Fatalf("tramites after double seed = %d, want %d", len(trams), want["tramites"])
	}
}

func TestSeedRejectsBadFK(t *testing.T) {
	bad := fstest.MapFS{
		"bad.json": &fstest.MapFile{Data: []byte(
			`{"tramites":[{"id":"t1","categoriaId":"missing","titulo":"x","entidad":"x","descripcion":"x","costo":"x","tiempoEstimado":"x","icono":"x"}]}`,
		)},
	}
	if err := SeedFromFS(context.Background(), NewMemory(), bad); err == nil {
		t.Fatal("SeedFromFS with dangling FK = nil error, want error")
	}
}

// Seed must not revert content edited after the first load; only missing seed
// rows are inserted. This is what keeps admin-panel edits across restarts.
func TestSeedDoesNotOverwriteExistingRows(t *testing.T) {
	ctx := context.Background()
	s := NewMemory()
	if err := Seed(ctx, s); err != nil {
		t.Fatalf("Seed: %v", err)
	}

	// Simulate an admin edit of a seeded categoria and a new, non-seed one.
	edited := Categoria{ID: "cat_personales", Nombre: "Editado a mano", Icono: "X"}
	if err := s.Categorias().Save(ctx, edited); err != nil {
		t.Fatal(err)
	}
	added := Categoria{ID: "cat_nueva", Nombre: "Nueva", Icono: "N"}
	if err := s.Categorias().Save(ctx, added); err != nil {
		t.Fatal(err)
	}

	if err := Seed(ctx, s); err != nil {
		t.Fatalf("Seed #2: %v", err)
	}

	got, err := s.Categorias().Get(ctx, "cat_personales")
	if err != nil {
		t.Fatal(err)
	}
	if got.Nombre != "Editado a mano" {
		t.Errorf("seeded row reverted: nombre = %q, want %q", got.Nombre, "Editado a mano")
	}
	if _, err := s.Categorias().Get(ctx, "cat_nueva"); err != nil {
		t.Errorf("non-seed row lost after re-seed: %v", err)
	}
}

// SeedOverwrite restores seed values, used by the prune tool to reset a DB.
func TestSeedOverwriteRevertsEdits(t *testing.T) {
	ctx := context.Background()
	s := NewMemory()
	if err := Seed(ctx, s); err != nil {
		t.Fatalf("Seed: %v", err)
	}
	if err := s.Categorias().Save(ctx, Categoria{ID: "cat_personales", Nombre: "Editado", Icono: "X"}); err != nil {
		t.Fatal(err)
	}
	if err := SeedOverwrite(ctx, s); err != nil {
		t.Fatalf("SeedOverwrite: %v", err)
	}
	got, err := s.Categorias().Get(ctx, "cat_personales")
	if err != nil {
		t.Fatal(err)
	}
	if got.Nombre == "Editado" {
		t.Error("SeedOverwrite left edited value; want seed value restored")
	}
}
