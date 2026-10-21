package store

import (
	"context"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type pgStore struct {
	db *pgxpool.Pool
	c  crud[Categoria]
	t  crud[Tramite]
	r  crud[Requisito]
	p  crud[Paso]
	d  crud[Delegacion]
	dt joinStore
}

func NewPostgres(pool *pgxpool.Pool) Store {
	return &pgStore{
		db: pool,
		c: newCrud[Categoria](pool, "categorias", "id", []string{"id", "nombre", "icono"},
			"INSERT INTO categorias (id,nombre,icono) VALUES ($1,$2,$3) ON CONFLICT (id) DO UPDATE SET nombre=EXCLUDED.nombre, icono=EXCLUDED.icono",
			func(r pgx.Row) (Categoria, error) { return scanCategoria(r) },
			func(v Categoria) []any { return []any{v.ID, v.Nombre, v.Icono} }),
		t: newCrud[Tramite](pool, "tramites", "id", []string{"id", "categoria_id", "titulo", "entidad", "descripcion", "costo", "tiempo_estimado", "icono", "is_quick_access"},
			"INSERT INTO tramites (id,categoria_id,titulo,entidad,descripcion,costo,tiempo_estimado,icono,is_quick_access) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9) ON CONFLICT (id) DO UPDATE SET categoria_id=EXCLUDED.categoria_id,titulo=EXCLUDED.titulo,entidad=EXCLUDED.entidad,descripcion=EXCLUDED.descripcion,costo=EXCLUDED.costo,tiempo_estimado=EXCLUDED.tiempo_estimado,icono=EXCLUDED.icono,is_quick_access=EXCLUDED.is_quick_access",
			func(r pgx.Row) (Tramite, error) { return scanTramite(r) },
			func(v Tramite) []any { return []any{v.ID, v.CategoriaID, v.Titulo, v.Entidad, v.Descripcion, v.Costo, v.TiempoEstimado, v.Icono, v.IsQuickAccess} }),
		r: newCrud[Requisito](pool, "requisitos", "id", []string{"id", "tramite_id", "descripcion", "es_tramite_previo", "nota"},
			"INSERT INTO requisitos (id,tramite_id,descripcion,es_tramite_previo,nota) VALUES ($1,$2,$3,$4,$5) ON CONFLICT (id) DO UPDATE SET tramite_id=EXCLUDED.tramite_id,descripcion=EXCLUDED.descripcion,es_tramite_previo=EXCLUDED.es_tramite_previo,nota=EXCLUDED.nota",
			func(r pgx.Row) (Requisito, error) { return scanRequisito(r) },
			func(v Requisito) []any {
				var n any = v.Nota
				if v.Nota == nil {
					n = nil
				}
				return []any{v.ID, v.TramiteID, v.Descripcion, v.EsTramitePrevio, n}
			}),
		p: newCrud[Paso](pool, "pasos", "id", []string{"id", "tramite_id", "orden", "descripcion"},
			"INSERT INTO pasos (id,tramite_id,orden,descripcion) VALUES ($1,$2,$3,$4) ON CONFLICT (id) DO UPDATE SET tramite_id=EXCLUDED.tramite_id,orden=EXCLUDED.orden,descripcion=EXCLUDED.descripcion",
			func(r pgx.Row) (Paso, error) { return scanPaso(r) },
			func(v Paso) []any { return []any{v.ID, v.TramiteID, v.Orden, v.Descripcion} }),
		d: newCrud[Delegacion](pool, "delegaciones", "id", []string{"id", "nombre_oficina", "departamento", "latitud", "longitud", "direccion"},
			"INSERT INTO delegaciones (id,nombre_oficina,departamento,latitud,longitud,direccion) VALUES ($1,$2,$3,$4,$5,$6) ON CONFLICT (id) DO UPDATE SET nombre_oficina=EXCLUDED.nombre_oficina,departamento=EXCLUDED.departamento,latitud=EXCLUDED.latitud,longitud=EXCLUDED.longitud,direccion=EXCLUDED.direccion",
			func(r pgx.Row) (Delegacion, error) { return scanDelegacion(r) },
			func(v Delegacion) []any { return []any{v.ID, v.NombreOficina, v.Departamento, v.Latitud, v.Longitud, v.Direccion} }),
		dt: joinStore{db: pool},
	}
}

func (s *pgStore) Feed(ctx context.Context) (*Feed, error) {
	f := &Feed{}
	var err error
	f.Categorias, err = s.c.List(ctx)
	if err != nil {
		return nil, err
	}
	f.Tramites, err = s.t.List(ctx)
	if err != nil {
		return nil, err
	}
	f.Requisitos, err = s.r.List(ctx)
	if err != nil {
		return nil, err
	}
	f.Pasos, err = s.p.List(ctx)
	if err != nil {
		return nil, err
	}
	f.Delegaciones, err = s.d.List(ctx)
	if err != nil {
		return nil, err
	}
	f.DelegacionTramites, err = s.dt.List(ctx)
	if err != nil {
		return nil, err
	}
	f.Normalize()
	return f, nil
}

func (s *pgStore) Categorias() CRUD[Categoria]    { return s.c }
func (s *pgStore) Tramites() CRUD[Tramite]        { return s.t }
func (s *pgStore) Requisitos() CRUD[Requisito]    { return s.r }
func (s *pgStore) Pasos() CRUD[Paso]              { return s.p }
func (s *pgStore) Delegaciones() CRUD[Delegacion] { return s.d }
func (s *pgStore) DelegacionTramites() JoinStore  { return s.dt }

type crud[T any] struct {
	db     *pgxpool.Pool
	table  string
	idCol  string
	cols   []string
	insert string
	scan   func(pgx.Row) (T, error)
	val    func(T) []any
}

func newCrud[T any](db *pgxpool.Pool, table, idCol string, cols []string, insert string, scan func(pgx.Row) (T, error), val func(T) []any) crud[T] {
	return crud[T]{db: db, table: table, idCol: idCol, cols: cols, insert: insert, scan: scan, val: val}
}

func (c crud[T]) List(ctx context.Context) ([]T, error) {
	q := "SELECT " + joinCols(c.cols) + " FROM " + c.table
	rows, err := c.db.Query(ctx, q)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var res []T
	for rows.Next() {
		v, err := c.scan(rows)
		if err != nil {
			return nil, err
		}
		res = append(res, v)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	if res == nil {
		res = []T{}
	}
	return res, nil
}

func (c crud[T]) Get(ctx context.Context, id string) (T, error) {
	var zero T
	q := "SELECT " + joinCols(c.cols) + " FROM " + c.table + " WHERE " + c.idCol + "=$1"
	row := c.db.QueryRow(ctx, q, id)
	v, err := c.scan(row)
	if err != nil {
		if err == pgx.ErrNoRows {
			return zero, ErrNotFound
		}
		return zero, err
	}
	return v, nil
}

func (c crud[T]) Save(ctx context.Context, item T) error {
	_, err := c.db.Exec(ctx, c.insert, c.val(item)...)
	return err
}

func (c crud[T]) Delete(ctx context.Context, id string) error {
	_, err := c.db.Exec(ctx, "DELETE FROM "+c.table+" WHERE "+c.idCol+"=$1", id)
	return err
}

func joinCols(cols []string) string {
	s := ""
	for i, c := range cols {
		if i > 0 {
			s += ","
		}
		s += c
	}
	return s
}
