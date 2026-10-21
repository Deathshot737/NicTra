package store

import (
	"context"
	"encoding/json"
	"fmt"
	"io/fs"
	"strings"
)

// SeedFromFS loads every *.json file in fsys (root, sorted by name) into the
// store. Each file is a feed-shaped JSON document matching the contract in
// AGENTS.md. All files are parsed and validated first, then rows are applied in
// FK-safe order. Existing rows are left untouched (see SeedOverwriteFromFS to
// force the seed values back), so re-seeding after an admin edit is safe.
func SeedFromFS(ctx context.Context, s Store, fsys fs.FS) error {
	return seedFromFS(ctx, s, fsys, false)
}

// SeedOverwriteFromFS is like SeedFromFS but overwrites rows that already exist
// with the seed values. Used by the prune tool to reset a database to exactly
// the shipped dataset.
func SeedOverwriteFromFS(ctx context.Context, s Store, fsys fs.FS) error {
	return seedFromFS(ctx, s, fsys, true)
}

func seedFromFS(ctx context.Context, s Store, fsys fs.FS, overwrite bool) error {
	entries, err := fs.ReadDir(fsys, ".")
	if err != nil {
		return fmt.Errorf("read seed dir: %w", err)
	}

	var feeds []*Feed
	var names []string
	for _, e := range entries {
		if e.IsDir() || !strings.HasSuffix(e.Name(), ".json") {
			continue
		}
		b, err := fs.ReadFile(fsys, e.Name())
		if err != nil {
			return fmt.Errorf("read %s: %w", e.Name(), err)
		}
		var f Feed
		if err := json.Unmarshal(b, &f); err != nil {
			return fmt.Errorf("parse %s: %w", e.Name(), err)
		}
		feeds = append(feeds, &f)
		names = append(names, e.Name())
	}
	if len(feeds) == 0 {
		return fmt.Errorf("no *.json seed files found")
	}

	for i, f := range feeds {
		if err := validateFeed(f); err != nil {
			return fmt.Errorf("%s: %w", names[i], err)
		}
	}
	for _, f := range feeds {
		if err := applyFeed(ctx, s, f, overwrite); err != nil {
			return err
		}
	}
	return nil
}

// applyFeed writes one feed document in FK-safe order. When overwrite is false,
// rows already present are left untouched (so re-seeding never reverts
// admin-panel edits) while new seed rows are inserted; when true, seed values
// replace existing rows.
func applyFeed(ctx context.Context, s Store, f *Feed, overwrite bool) error {
	for _, c := range f.Categorias {
		if err := applyRow(ctx, s.Categorias(), c.ID, c, overwrite); err != nil {
			return fmt.Errorf("seed categoria %q: %w", c.ID, err)
		}
	}
	for _, t := range f.Tramites {
		if err := applyRow(ctx, s.Tramites(), t.ID, t, overwrite); err != nil {
			return fmt.Errorf("seed tramite %q: %w", t.ID, err)
		}
	}
	for _, r := range f.Requisitos {
		if err := applyRow(ctx, s.Requisitos(), r.ID, r, overwrite); err != nil {
			return fmt.Errorf("seed requisito %q: %w", r.ID, err)
		}
	}
	for _, p := range f.Pasos {
		if err := applyRow(ctx, s.Pasos(), p.ID, p, overwrite); err != nil {
			return fmt.Errorf("seed paso %q: %w", p.ID, err)
		}
	}
	for _, d := range f.Delegaciones {
		if err := applyRow(ctx, s.Delegaciones(), d.ID, d, overwrite); err != nil {
			return fmt.Errorf("seed delegacion %q: %w", d.ID, err)
		}
	}
	for _, dt := range f.DelegacionTramites {
		if err := s.DelegacionTramites().Add(ctx, dt); err != nil {
			return fmt.Errorf("seed join %q->%q: %w", dt.DelegacionID, dt.TramiteID, err)
		}
	}
	return nil
}

// applyRow inserts item when id is absent, or overwrites it when overwrite is
// true and the row already exists.
func applyRow[T any](ctx context.Context, c CRUD[T], id string, item T, overwrite bool) error {
	if _, err := c.Get(ctx, id); err == nil {
		if !overwrite {
			return nil
		}
		return c.Save(ctx, item)
	} else if err != ErrNotFound {
		return err
	}
	return c.Save(ctx, item)
}

// validateFeed checks required ids, duplicate ids and FK references so bad
// seed files fail with a clear message instead of a DB error (or silently
// corrupt rows in the memory store, which enforces no FKs).
func validateFeed(f *Feed) error {
	catIDs, tramIDs, delIDs := map[string]bool{}, map[string]bool{}, map[string]bool{}
	seen := map[string]map[string]bool{}

	dup := func(kind, id string) error {
		if seen[kind] == nil {
			seen[kind] = map[string]bool{}
		}
		if seen[kind][id] {
			return fmt.Errorf("duplicate %s id %q", kind, id)
		}
		seen[kind][id] = true
		return nil
	}

	for _, c := range f.Categorias {
		if c.ID == "" {
			return fmt.Errorf("categoria without id")
		}
		if err := dup("categoria", c.ID); err != nil {
			return err
		}
		catIDs[c.ID] = true
	}
	for _, t := range f.Tramites {
		if t.ID == "" {
			return fmt.Errorf("tramite without id")
		}
		if err := dup("tramite", t.ID); err != nil {
			return err
		}
		if !catIDs[t.CategoriaID] {
			return fmt.Errorf("tramite %q references unknown categoria %q", t.ID, t.CategoriaID)
		}
		tramIDs[t.ID] = true
	}
	for _, r := range f.Requisitos {
		if r.ID == "" {
			return fmt.Errorf("requisito without id")
		}
		if err := dup("requisito", r.ID); err != nil {
			return err
		}
		if !tramIDs[r.TramiteID] {
			return fmt.Errorf("requisito %q references unknown tramite %q", r.ID, r.TramiteID)
		}
	}
	for _, p := range f.Pasos {
		if p.ID == "" {
			return fmt.Errorf("paso without id")
		}
		if err := dup("paso", p.ID); err != nil {
			return err
		}
		if !tramIDs[p.TramiteID] {
			return fmt.Errorf("paso %q references unknown tramite %q", p.ID, p.TramiteID)
		}
	}
	for _, d := range f.Delegaciones {
		if d.ID == "" {
			return fmt.Errorf("delegacion without id")
		}
		if err := dup("delegacion", d.ID); err != nil {
			return err
		}
		delIDs[d.ID] = true
	}
	for _, dt := range f.DelegacionTramites {
		if !delIDs[dt.DelegacionID] {
			return fmt.Errorf("join references unknown delegacion %q", dt.DelegacionID)
		}
		if !tramIDs[dt.TramiteID] {
			return fmt.Errorf("join references unknown tramite %q", dt.TramiteID)
		}
	}
	return nil
}
