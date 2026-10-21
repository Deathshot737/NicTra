package store

import "time"

// Feed matches the fixed JSON contract.
type Feed struct {
	Categorias         []Categoria         `json:"categorias"`
	Tramites           []Tramite           `json:"tramites"`
	Requisitos         []Requisito         `json:"requisitos"`
	Pasos              []Paso              `json:"pasos"`
	Delegaciones       []Delegacion        `json:"delegaciones"`
	DelegacionTramites []DelegacionTramite `json:"delegacion_tramites"`
}

// Ensure slices non-nil on marshal? caller can ensure.
func (f *Feed) Normalize() {
	if f.Categorias == nil {
		f.Categorias = []Categoria{}
	}
	if f.Tramites == nil {
		f.Tramites = []Tramite{}
	}
	if f.Requisitos == nil {
		f.Requisitos = []Requisito{}
	}
	if f.Pasos == nil {
		f.Pasos = []Paso{}
	}
	if f.Delegaciones == nil {
		f.Delegaciones = []Delegacion{}
	}
	if f.DelegacionTramites == nil {
		f.DelegacionTramites = []DelegacionTramite{}
	}
}

type Categoria struct {
	ID     string `json:"id"`
	Nombre string `json:"nombre"`
	Icono  string `json:"icono"`
}

type Tramite struct {
	ID             string `json:"id"`
	CategoriaID    string `json:"categoriaId"`
	Titulo         string `json:"titulo"`
	Entidad        string `json:"entidad"`
	Descripcion    string `json:"descripcion"`
	Costo          string `json:"costo"`
	TiempoEstimado string `json:"tiempoEstimado"`
	Icono          string `json:"icono"`
	IsQuickAccess  bool   `json:"isQuickAccess,omitempty"`
}

type Requisito struct {
	ID             string  `json:"id"`
	TramiteID      string  `json:"tramiteId"`
	Descripcion    string  `json:"descripcion"`
	EsTramitePrevio bool    `json:"esTramitePrevio"`
	Nota           *string `json:"nota,omitempty"`
}

type Paso struct {
	ID          string `json:"id"`
	TramiteID   string `json:"tramiteId"`
	Orden       int    `json:"orden"`
	Descripcion string `json:"descripcion"`
}

type Delegacion struct {
	ID            string  `json:"id"`
	NombreOficina string  `json:"nombreOficina"`
	Departamento  string  `json:"departamento"`
	Latitud       float64 `json:"latitud"`
	Longitud      float64 `json:"longitud"`
	Direccion     string  `json:"direccion"`
}

type DelegacionTramite struct {
	DelegacionID string `json:"delegacionId"`
	TramiteID    string `json:"tramiteId"`
}

type Migration struct {
	Version   string
	AppliedAt time.Time
}
