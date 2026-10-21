package store

import (
	"context"
	"sort"
	"sync"
)

// Memory is a thread-safe in-memory Store for tests and local runs without Postgres.
type Memory struct {
	mu   sync.RWMutex
	cat  map[string]Categoria
	tram map[string]Tramite
	req  map[string]Requisito
	pas  map[string]Paso
	del  map[string]Delegacion
	j    map[string]DelegacionTramite // key: delegacionID + "|" + tramiteID
}

func NewMemory() *Memory {
	return &Memory{
		cat:  map[string]Categoria{},
		tram: map[string]Tramite{},
		req:  map[string]Requisito{},
		pas:  map[string]Paso{},
		del:  map[string]Delegacion{},
		j:    map[string]DelegacionTramite{},
	}
}

func dtKey(d, t string) string { return d + "|" + t }

// --- Feed ---
func (m *Memory) Feed(ctx context.Context) (*Feed, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()
	f := &Feed{
		Categorias:         []Categoria{},
		Tramites:           []Tramite{},
		Requisitos:         []Requisito{},
		Pasos:              []Paso{},
		Delegaciones:       []Delegacion{},
		DelegacionTramites: []DelegacionTramite{},
	}
	for _, c := range m.cat {
		f.Categorias = append(f.Categorias, c)
	}
	for _, t := range m.tram {
		f.Tramites = append(f.Tramites, t)
	}
	for _, r := range m.req {
		f.Requisitos = append(f.Requisitos, r)
	}
	for _, p := range m.pas {
		f.Pasos = append(f.Pasos, p)
	}
	for _, d := range m.del {
		f.Delegaciones = append(f.Delegaciones, d)
	}
	for _, dt := range m.j {
		f.DelegacionTramites = append(f.DelegacionTramites, dt)
	}
	sort.Slice(f.Categorias, func(i, j int) bool { return f.Categorias[i].ID < f.Categorias[j].ID })
	sort.Slice(f.Tramites, func(i, j int) bool { return f.Tramites[i].ID < f.Tramites[j].ID })
	sort.Slice(f.Requisitos, func(i, j int) bool { return f.Requisitos[i].ID < f.Requisitos[j].ID })
	sort.Slice(f.Pasos, func(i, j int) bool { return f.Pasos[i].ID < f.Pasos[j].ID })
	sort.Slice(f.Delegaciones, func(i, j int) bool { return f.Delegaciones[i].ID < f.Delegaciones[j].ID })
	sort.Slice(f.DelegacionTramites, func(i, j int) bool {
		if f.DelegacionTramites[i].DelegacionID != f.DelegacionTramites[j].DelegacionID {
			return f.DelegacionTramites[i].DelegacionID < f.DelegacionTramites[j].DelegacionID
		}
		return f.DelegacionTramites[i].TramiteID < f.DelegacionTramites[j].TramiteID
	})
	return f, nil
}

// --- generic in-memory CRUD ---
type memCRUD[T any] struct {
	mu    *sync.RWMutex
	m     *map[string]T
	key   func(T) string
	order func([]T)
}

func (c memCRUD[T]) List(ctx context.Context) ([]T, error) {
	c.mu.RLock()
	defer c.mu.RUnlock()
	out := make([]T, 0, len(*c.m))
	for _, v := range *c.m {
		out = append(out, v)
	}
	if c.order != nil {
		c.order(out)
	}
	return out, nil
}

func (c memCRUD[T]) Get(ctx context.Context, id string) (T, error) {
	c.mu.RLock()
	defer c.mu.RUnlock()
	v, ok := (*c.m)[id]
	if !ok {
		var zero T
		return zero, ErrNotFound
	}
	return v, nil
}

func (c memCRUD[T]) Save(ctx context.Context, item T) error {
	c.mu.Lock()
	defer c.mu.Unlock()
	(*c.m)[c.key(item)] = item
	return nil
}

func (c memCRUD[T]) Delete(ctx context.Context, id string) error {
	c.mu.Lock()
	defer c.mu.Unlock()
	if _, ok := (*c.m)[id]; !ok {
		return ErrNotFound
	}
	delete(*c.m, id)
	return nil
}

func (m *Memory) Categorias() CRUD[Categoria] {
	return memCRUD[Categoria]{mu: &m.mu, m: &m.cat, key: func(c Categoria) string { return c.ID },
		order: func(s []Categoria) { sort.Slice(s, func(i, j int) bool { return s[i].ID < s[j].ID }) }}
}

func (m *Memory) Tramites() CRUD[Tramite] {
	return memCRUD[Tramite]{mu: &m.mu, m: &m.tram, key: func(t Tramite) string { return t.ID },
		order: func(s []Tramite) { sort.Slice(s, func(i, j int) bool { return s[i].ID < s[j].ID }) }}
}

func (m *Memory) Requisitos() CRUD[Requisito] {
	return memCRUD[Requisito]{mu: &m.mu, m: &m.req, key: func(r Requisito) string { return r.ID },
		order: func(s []Requisito) { sort.Slice(s, func(i, j int) bool { return s[i].ID < s[j].ID }) }}
}

func (m *Memory) Pasos() CRUD[Paso] {
	return memCRUD[Paso]{mu: &m.mu, m: &m.pas, key: func(p Paso) string { return p.ID },
		order: func(s []Paso) { sort.Slice(s, func(i, j int) bool { return s[i].ID < s[j].ID }) }}
}

func (m *Memory) Delegaciones() CRUD[Delegacion] {
	return memCRUD[Delegacion]{mu: &m.mu, m: &m.del, key: func(d Delegacion) string { return d.ID },
		order: func(s []Delegacion) { sort.Slice(s, func(i, j int) bool { return s[i].ID < s[j].ID }) }}
}

type memJoin struct {
	mu *sync.RWMutex
	j  *map[string]DelegacionTramite
}

func (m *Memory) DelegacionTramites() JoinStore {
	return memJoin{mu: &m.mu, j: &m.j}
}

func (j memJoin) List(ctx context.Context) ([]DelegacionTramite, error) {
	j.mu.RLock()
	defer j.mu.RUnlock()
	out := make([]DelegacionTramite, 0, len(*j.j))
	for _, v := range *j.j {
		out = append(out, v)
	}
	sort.Slice(out, func(i, k int) bool {
		if out[i].DelegacionID != out[k].DelegacionID {
			return out[i].DelegacionID < out[k].DelegacionID
		}
		return out[i].TramiteID < out[k].TramiteID
	})
	return out, nil
}

func (j memJoin) Add(ctx context.Context, item DelegacionTramite) error {
	j.mu.Lock()
	defer j.mu.Unlock()
	(*j.j)[dtKey(item.DelegacionID, item.TramiteID)] = item
	return nil
}

func (j memJoin) Remove(ctx context.Context, delegacionID, tramiteID string) error {
	j.mu.Lock()
	defer j.mu.Unlock()
	k := dtKey(delegacionID, tramiteID)
	if _, ok := (*j.j)[k]; !ok {
		return ErrNotFound
	}
	delete(*j.j, k)
	return nil
}

// Ensure Memory satisfies Store.
var _ Store = (*Memory)(nil)
