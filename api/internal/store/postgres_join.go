package store

import (
	"context"

	"github.com/jackc/pgx/v5/pgxpool"
)

type joinStore struct {
	db *pgxpool.Pool
}

func (j joinStore) List(ctx context.Context) ([]DelegacionTramite, error) {
	rows, err := j.db.Query(ctx, "SELECT delegacion_id, tramite_id FROM delegacion_tramites")
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var res []DelegacionTramite
	for rows.Next() {
		var dt DelegacionTramite
		if err := rows.Scan(&dt.DelegacionID, &dt.TramiteID); err != nil {
			return nil, err
		}
		res = append(res, dt)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	if res == nil {
		res = []DelegacionTramite{}
	}
	return res, nil
}

func (j joinStore) Add(ctx context.Context, item DelegacionTramite) error {
	_, err := j.db.Exec(ctx, "INSERT INTO delegacion_tramites (delegacion_id, tramite_id) VALUES ($1,$2) ON CONFLICT DO NOTHING", item.DelegacionID, item.TramiteID)
	return err
}

func (j joinStore) Remove(ctx context.Context, delegacionID, tramiteID string) error {
	_, err := j.db.Exec(ctx, "DELETE FROM delegacion_tramites WHERE delegacion_id=$1 AND tramite_id=$2", delegacionID, tramiteID)
	return err
}
