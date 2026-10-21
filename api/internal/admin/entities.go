package admin

import (
	"context"
	"encoding/json"
	"net/http"

	"contentapi/internal/store"
)

// Option is a value/label pair for <select> fields.
type Option struct {
	Value string
	Label string
}

// Field describes one column/field of an admin form. Names match the JSON
// contract field names exactly (Spanish), so form values map 1:1 onto models.
type Field struct {
	Name     string
	Label    string
	Type     string // text | textarea | number | int | checkbox | select
	Required bool
	Optional bool
	Help     string
	Options  func(ctx context.Context) ([]Option, error)
}

// Entity is a type-erased view over one CRUD-able table so the admin UI can
// render lists and forms without per-table templates.
type Entity struct {
	Slug    string
	Label   string
	IDField string
	Fields  []Field
	IsJoin  bool

	List   func(ctx context.Context) ([]map[string]any, error)
	Get    func(ctx context.Context, id string) (map[string]any, error)
	Save   func(ctx context.Context, m map[string]any) error
	Delete func(ctx context.Context, m map[string]any) error
}

func toJSONMap(v any) map[string]any {
	b, _ := json.Marshal(v)
	var m map[string]any
	_ = json.Unmarshal(b, &m)
	return m
}

func fromJSONMap[T any](m map[string]any) (T, error) {
	var zero T
	b, err := json.Marshal(m)
	if err != nil {
		return zero, err
	}
	var out T
	if err := json.Unmarshal(b, &out); err != nil {
		return zero, err
	}
	return out, nil
}

// makeEntity wires a typed CRUD into the generic Entity shape.
func makeEntity[T any](slug, label, idField string, fields []Field, c store.CRUD[T]) Entity {
	e := Entity{
		Slug:    slug,
		Label:   label,
		IDField: idField,
		Fields:  fields,
		List: func(ctx context.Context) ([]map[string]any, error) {
			items, err := c.List(ctx)
			if err != nil {
				return nil, err
			}
			out := make([]map[string]any, 0, len(items))
			for _, it := range items {
				out = append(out, toJSONMap(it))
			}
			return out, nil
		},
		Get: func(ctx context.Context, id string) (map[string]any, error) {
			it, err := c.Get(ctx, id)
			if err != nil {
				return nil, err
			}
			return toJSONMap(it), nil
		},
		Save: func(ctx context.Context, m map[string]any) error {
			it, err := fromJSONMap[T](m)
			if err != nil {
				return err
			}
			return c.Save(ctx, it)
		},
		Delete: func(ctx context.Context, m map[string]any) error {
			id, _ := m[idField].(string)
			return c.Delete(ctx, id)
		},
	}
	return e
}

// makeJoin wires the delegacion_tramites join table (composite key, no id).
func makeJoin(slug, label string, fields []Field, js store.JoinStore) Entity {
	return Entity{
		Slug:   slug,
		Label:  label,
		IsJoin: true,
		Fields: fields,
		List: func(ctx context.Context) ([]map[string]any, error) {
			items, err := js.List(ctx)
			if err != nil {
				return nil, err
			}
			out := make([]map[string]any, 0, len(items))
			for _, it := range items {
				out = append(out, toJSONMap(it))
			}
			return out, nil
		},
		Get: func(ctx context.Context, id string) (map[string]any, error) {
			return nil, store.ErrNotFound
		},
		Save: func(ctx context.Context, m map[string]any) error {
			it, err := fromJSONMap[store.DelegacionTramite](m)
			if err != nil {
				return err
			}
			return js.Add(ctx, it)
		},
		Delete: func(ctx context.Context, m map[string]any) error {
			d, _ := m["delegacionId"].(string)
			t, _ := m["tramiteId"].(string)
			return js.Remove(ctx, d, t)
		},
	}
}

// entities builds the full admin registry from a Store.
func entities(s store.Store) []Entity {
	categoriaOptions := func(ctx context.Context) ([]Option, error) {
		all, err := s.Categorias().List(ctx)
		if err != nil {
			return nil, err
		}
		opts := make([]Option, 0, len(all))
		for _, c := range all {
			opts = append(opts, Option{Value: c.ID, Label: c.Nombre + " (" + c.ID + ")"})
		}
		return opts, nil
	}
	tramiteOptions := func(ctx context.Context) ([]Option, error) {
		all, err := s.Tramites().List(ctx)
		if err != nil {
			return nil, err
		}
		opts := make([]Option, 0, len(all))
		for _, t := range all {
			opts = append(opts, Option{Value: t.ID, Label: t.Titulo + " (" + t.ID + ")"})
		}
		return opts, nil
	}
	delegacionOptions := func(ctx context.Context) ([]Option, error) {
		all, err := s.Delegaciones().List(ctx)
		if err != nil {
			return nil, err
		}
		opts := make([]Option, 0, len(all))
		for _, d := range all {
			opts = append(opts, Option{Value: d.ID, Label: d.NombreOficina + " (" + d.ID + ")"})
		}
		return opts, nil
	}

	return []Entity{
		makeEntity("categorias", "Categorías", "id", []Field{
			{Name: "id", Label: "ID", Type: "text", Required: true},
			{Name: "nombre", Label: "Nombre", Type: "text", Required: true},
			{Name: "icono", Label: "Icono", Type: "text", Required: true},
		}, s.Categorias()),

		makeEntity("tramites", "Trámites", "id", []Field{
			{Name: "id", Label: "ID", Type: "text", Required: true},
			{Name: "categoriaId", Label: "Categoría", Type: "select", Required: true, Options: categoriaOptions},
			{Name: "titulo", Label: "Título", Type: "text", Required: true},
			{Name: "entidad", Label: "Entidad", Type: "text", Required: true},
			{Name: "descripcion", Label: "Descripción", Type: "textarea", Required: true},
			{Name: "costo", Label: "Costo", Type: "text", Required: true},
			{Name: "tiempoEstimado", Label: "Tiempo estimado", Type: "text", Required: true},
			{Name: "icono", Label: "Icono", Type: "text", Required: true},
			{Name: "isQuickAccess", Label: "Acceso rápido", Type: "checkbox"},
		}, s.Tramites()),

		makeEntity("requisitos", "Requisitos", "id", []Field{
			{Name: "id", Label: "ID", Type: "text", Required: true},
			{Name: "tramiteId", Label: "Trámite", Type: "select", Required: true, Options: tramiteOptions},
			{Name: "descripcion", Label: "Descripción", Type: "textarea", Required: true},
			{Name: "esTramitePrevio", Label: "Es trámite previo", Type: "checkbox"},
			{Name: "nota", Label: "Nota", Type: "textarea", Optional: true},
		}, s.Requisitos()),

		makeEntity("pasos", "Pasos", "id", []Field{
			{Name: "id", Label: "ID", Type: "text", Required: true},
			{Name: "tramiteId", Label: "Trámite", Type: "select", Required: true, Options: tramiteOptions},
			{Name: "orden", Label: "Orden", Type: "int", Required: true},
			{Name: "descripcion", Label: "Descripción", Type: "textarea", Required: true},
		}, s.Pasos()),

		makeEntity("delegaciones", "Delegaciones", "id", []Field{
			{Name: "id", Label: "ID", Type: "text", Required: true},
			{Name: "nombreOficina", Label: "Nombre de oficina", Type: "text", Required: true},
			{Name: "departamento", Label: "Departamento", Type: "text", Required: true},
			{Name: "latitud", Label: "Latitud", Type: "number", Required: true},
			{Name: "longitud", Label: "Longitud", Type: "number", Required: true},
			{Name: "direccion", Label: "Dirección", Type: "textarea", Required: true},
		}, s.Delegaciones()),

		makeJoin("delegacion_tramites", "Delegación ↔ Trámite", []Field{
			{Name: "delegacionId", Label: "Delegación", Type: "select", Required: true, Options: delegacionOptions},
			{Name: "tramiteId", Label: "Trámite", Type: "select", Required: true, Options: tramiteOptions},
		}, s.DelegacionTramites()),
	}
}

func entityBySlug(ents []Entity, slug string) *Entity {
	for i := range ents {
		if ents[i].Slug == slug {
			return &ents[i]
		}
	}
	return nil
}

var _ = http.StatusOK // keep net/http referenced if unused later
