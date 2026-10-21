package store

import (
	"github.com/jackc/pgx/v5"
)

func isNoRows(err error) bool {
	return err == pgx.ErrNoRows
}

// scans
func scanCategoria(row pgx.Row) (Categoria, error) {
	var c Categoria
	err := row.Scan(&c.ID, &c.Nombre, &c.Icono)
	return c, err
}

func scanTramite(row pgx.Row) (Tramite, error) {
	var t Tramite
	err := row.Scan(&t.ID, &t.CategoriaID, &t.Titulo, &t.Entidad, &t.Descripcion, &t.Costo, &t.TiempoEstimado, &t.Icono, &t.IsQuickAccess)
	return t, err
}

func scanRequisito(row pgx.Row) (Requisito, error) {
	var r Requisito
	var nota *string
	err := row.Scan(&r.ID, &r.TramiteID, &r.Descripcion, &r.EsTramitePrevio, &nota)
	if err != nil {
		return r, err
	}
	r.Nota = nota
	return r, err
}

func scanPaso(row pgx.Row) (Paso, error) {
	var p Paso
	err := row.Scan(&p.ID, &p.TramiteID, &p.Orden, &p.Descripcion)
	return p, err
}

func scanDelegacion(row pgx.Row) (Delegacion, error) {
	var d Delegacion
	err := row.Scan(&d.ID, &d.NombreOficina, &d.Departamento, &d.Latitud, &d.Longitud, &d.Direccion)
	return d, err
}

func scanDT(row pgx.Row) (DelegacionTramite, error) {
	var dt DelegacionTramite
	err := row.Scan(&dt.DelegacionID, &dt.TramiteID)
	return dt, err
}
