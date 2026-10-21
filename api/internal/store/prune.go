package store

import (
	"context"
	"encoding/json"
	"fmt"
	"io/fs"
	"strings"

	data "contentapi/internal/Data"
)

// Prune removes every row that is not defined in the embedded seed files (see
// PruneFromFS). Used at startup so a persistent database stays aligned with the
// shipped dataset.
func Prune(ctx context.Context, s Store) error {
	return PruneFromFS(ctx, s, data.FS)
}

// PruneFromFS deletes every row whose id is not present in the seed files.
// Children of removed categorias/tramites go through ON DELETE CASCADE; the
// delegacion_tramites join (composite key, no id) is pruned by pair.
func PruneFromFS(ctx context.Context, s Store, fsys fs.FS) error {
	keep, err := readFeeds(fsys)
	if err != nil {
		return err
	}

	delCats, err := removed(ctx, s.Categorias(), keep.catIDs)
	if err != nil {
		return fmt.Errorf("list categorias: %w", err)
	}
	delTrams, err := removed(ctx, s.Tramites(), keep.tramIDs)
	if err != nil {
		return fmt.Errorf("list tramites: %w", err)
	}
	delReqs, err := removed(ctx, s.Requisitos(), keep.reqIDs)
	if err != nil {
		return fmt.Errorf("list requisitos: %w", err)
	}
	delPasos, err := removed(ctx, s.Pasos(), keep.pasoIDs)
	if err != nil {
		return fmt.Errorf("list pasos: %w", err)
	}
	delDels, err := removed(ctx, s.Delegaciones(), keep.delIDs)
	if err != nil {
		return fmt.Errorf("list delegaciones: %w", err)
	}

	for _, id := range delReqs {
		if err := s.Requisitos().Delete(ctx, id); err != nil {
			return fmt.Errorf("prune requisito %q: %w", id, err)
		}
	}
	for _, id := range delPasos {
		if err := s.Pasos().Delete(ctx, id); err != nil {
			return fmt.Errorf("prune paso %q: %w", id, err)
		}
	}
	if err := pruneJoin(ctx, s, keep.join); err != nil {
		return err
	}
	for _, id := range delTrams {
		if err := s.Tramites().Delete(ctx, id); err != nil {
			return fmt.Errorf("prune tramite %q: %w", id, err)
		}
	}
	for _, id := range delDels {
		if err := s.Delegaciones().Delete(ctx, id); err != nil {
			return fmt.Errorf("prune delegacion %q: %w", id, err)
		}
	}
	for _, id := range delCats {
		if err := s.Categorias().Delete(ctx, id); err != nil {
			return fmt.Errorf("prune categoria %q: %w", id, err)
		}
	}
	return nil
}

type seedIDs struct {
	catIDs  map[string]bool
	tramIDs map[string]bool
	reqIDs  map[string]bool
	pasoIDs map[string]bool
	delIDs  map[string]bool
	join    map[string]bool // delegacionID + "|" + tramiteID
}

func readFeeds(fsys fs.FS) (*seedIDs, error) {
	entries, err := fs.ReadDir(fsys, ".")
	if err != nil {
		return nil, fmt.Errorf("read seed dir: %w", err)
	}
	k := &seedIDs{
		catIDs:  map[string]bool{},
		tramIDs: map[string]bool{},
		reqIDs:  map[string]bool{},
		pasoIDs: map[string]bool{},
		delIDs:  map[string]bool{},
		join:    map[string]bool{},
	}
	found := false
	for _, e := range entries {
		if e.IsDir() || !strings.HasSuffix(e.Name(), ".json") {
			continue
		}
		b, err := fs.ReadFile(fsys, e.Name())
		if err != nil {
			return nil, fmt.Errorf("read %s: %w", e.Name(), err)
		}
		var f Feed
		if err := json.Unmarshal(b, &f); err != nil {
			return nil, fmt.Errorf("parse %s: %w", e.Name(), err)
		}
		for _, c := range f.Categorias {
			k.catIDs[c.ID] = true
		}
		for _, t := range f.Tramites {
			k.tramIDs[t.ID] = true
		}
		for _, r := range f.Requisitos {
			k.reqIDs[r.ID] = true
		}
		for _, p := range f.Pasos {
			k.pasoIDs[p.ID] = true
		}
		for _, d := range f.Delegaciones {
			k.delIDs[d.ID] = true
		}
		for _, dt := range f.DelegacionTramites {
			k.join[dt.DelegacionID+"|"+dt.TramiteID] = true
		}
		found = true
	}
	if !found {
		return nil, fmt.Errorf("no *.json seed files found")
	}
	return k, nil
}

// removed returns the ids currently in c that are not in keep.
func removed[T any](ctx context.Context, c CRUD[T], keep map[string]bool) ([]string, error) {
	items, err := c.List(ctx)
	if err != nil {
		return nil, err
	}
	var out []string
	for _, it := range items {
		if id := itemID(it); !keep[id] {
			out = append(out, id)
		}
	}
	return out, nil
}

func pruneJoin(ctx context.Context, s Store, keep map[string]bool) error {
	got, err := s.DelegacionTramites().List(ctx)
	if err != nil {
		return fmt.Errorf("list join: %w", err)
	}
	for _, dt := range got {
		if !keep[dt.DelegacionID+"|"+dt.TramiteID] {
			if err := s.DelegacionTramites().Remove(ctx, dt.DelegacionID, dt.TramiteID); err != nil {
				return fmt.Errorf("prune join %q->%q: %w", dt.DelegacionID, dt.TramiteID, err)
			}
		}
	}
	return nil
}

// itemID extracts the JSON "id" field from a model. All pruned models have it.
func itemID(v any) string {
	b, _ := json.Marshal(v)
	var m struct {
		ID string `json:"id"`
	}
	_ = json.Unmarshal(b, &m)
	return m.ID
}
