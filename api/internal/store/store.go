package store

import (
	"context"
	"errors"
)

var (
	ErrNotFound = errors.New("not found")
)

type CRUD[T any] interface {
	List(ctx context.Context) ([]T, error)
	Get(ctx context.Context, id string) (T, error)
	Save(ctx context.Context, item T) error
	Delete(ctx context.Context, id string) error
}

type JoinStore interface {
	List(ctx context.Context) ([]DelegacionTramite, error)
	Add(ctx context.Context, item DelegacionTramite) error
	Remove(ctx context.Context, delegacionID, tramiteID string) error
}

type Store interface {
	Feed(ctx context.Context) (*Feed, error)
	Categorias() CRUD[Categoria]
	Tramites() CRUD[Tramite]
	Requisitos() CRUD[Requisito]
	Pasos() CRUD[Paso]
	Delegaciones() CRUD[Delegacion]
	DelegacionTramites() JoinStore
}

type spec[T any] struct {
	table    string
	idCol    string
	cols     []string
	insert   string
	update   string // optional; if empty use insert+conflict
	scanRow  func() []any
	scanVals func([]any) (T, error)
	// keep typed
}
