package admin

import (
	"context"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"

	"contentapi/internal/store"
)

func postForm(t *testing.T, a *Admin, path string, form url.Values) *httptest.ResponseRecorder {
	t.Helper()
	req := httptest.NewRequest(http.MethodPost, path, strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.Header.Set("Authorization", "Bearer test-token")
	rec := httptest.NewRecorder()
	a.ServeHTTP(rec, req)
	return rec
}

func postCreate(t *testing.T, a *Admin, form url.Values) *httptest.ResponseRecorder {
	t.Helper()
	return postForm(t, a, "/admin/categorias/new", form)
}

func seedTramiteDeps(t *testing.T, s store.Store) {
	t.Helper()
	ctx := context.Background()
	if err := s.Categorias().Save(ctx, store.Categoria{ID: "cat-1", Nombre: "Identidad", Icono: "id"}); err != nil {
		t.Fatal(err)
	}
	if err := s.Delegaciones().Save(ctx, store.Delegacion{
		ID: "del-1", NombreOficina: "CSE - Delegación Municipal León", Departamento: "Managua",
		Latitud: 12.13, Longitud: -86.25, Direccion: "Calle 1",
	}); err != nil {
		t.Fatal(err)
	}
}

func tramiteForm(id string) url.Values {
	return url.Values{
		"id":             {id},
		"categoriaId":    {"cat-1"},
		"titulo":         {"Pasaporte"},
		"descripcion":    {"Tramitar el pasaporte"},
		"costo":          {"C$ 100"},
		"tiempoEstimado": {"5 días"},
		"icono":          {"passport"},
	}
}

func TestTramiteFormAssignsDelegaciones(t *testing.T) {
	ctx := context.Background()
	s := store.NewMemory()
	seedTramiteDeps(t, s)
	a, err := New(s, "test-token", nil)
	if err != nil {
		t.Fatal(err)
	}

	form := tramiteForm("tra-1")
	form.Set("link:delegacionId", "del-1")
	rec := postForm(t, a, "/admin/tramites/new", form)
	if rec.Code != http.StatusSeeOther {
		t.Fatalf("create = %d, want 303 (body %q)", rec.Code, rec.Body.String())
	}
	pairs, err := s.DelegacionTramites().List(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if len(pairs) != 1 || pairs[0].DelegacionID != "del-1" || pairs[0].TramiteID != "tra-1" {
		t.Fatalf("pairs = %+v, want del-1/tra-1", pairs)
	}

	// The edit form renders the existing assignment checked.
	req := httptest.NewRequest(http.MethodGet, "/admin/tramites/edit?id=tra-1", nil)
	req.Header.Set("Authorization", "Bearer test-token")
	dash := httptest.NewRecorder()
	a.ServeHTTP(dash, req)
	if !strings.Contains(dash.Body.String(), `value="del-1" checked`) {
		t.Fatal("edit form missing checked delegación checkbox")
	}

	// Unchecking on update removes the pair.
	rec = postForm(t, a, "/admin/tramites/edit", tramiteForm("tra-1"))
	if rec.Code != http.StatusSeeOther {
		t.Fatalf("update = %d, want 303 (body %q)", rec.Code, rec.Body.String())
	}
	pairs, _ = s.DelegacionTramites().List(ctx)
	if len(pairs) != 0 {
		t.Fatalf("pairs after update = %+v, want none", pairs)
	}
}

func TestDeletingTramiteRemovesLinks(t *testing.T) {
	ctx := context.Background()
	s := store.NewMemory()
	seedTramiteDeps(t, s)
	a, err := New(s, "test-token", nil)
	if err != nil {
		t.Fatal(err)
	}
	if err := s.Tramites().Save(ctx, store.Tramite{ID: "tra-1", CategoriaID: "cat-1", Titulo: "X"}); err != nil {
		t.Fatal(err)
	}
	if err := s.DelegacionTramites().Add(ctx, store.DelegacionTramite{DelegacionID: "del-1", TramiteID: "tra-1"}); err != nil {
		t.Fatal(err)
	}

	rec := postForm(t, a, "/admin/tramites/delete", url.Values{"id": {"tra-1"}})
	if rec.Code != http.StatusSeeOther {
		t.Fatalf("delete = %d, want 303", rec.Code)
	}
	pairs, _ := s.DelegacionTramites().List(ctx)
	if len(pairs) != 0 {
		t.Fatalf("pairs after delete = %+v, want none", pairs)
	}
}

func TestOnChangeFiresAfterCreate(t *testing.T) {
	s := store.NewMemory()
	calls := 0
	a, err := New(s, "test-token", func() { calls++ })
	if err != nil {
		t.Fatal(err)
	}

	rec := postCreate(t, a, url.Values{"nombre": {"Personales"}, "icono": {"x"}})
	if rec.Code != http.StatusSeeOther {
		t.Fatalf("create = %d, want 303 (body %q)", rec.Code, rec.Body.String())
	}
	if calls != 1 {
		t.Fatalf("onChange calls = %d, want 1", calls)
	}
	cats, err := s.Categorias().List(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	if len(cats) != 1 || !strings.HasPrefix(cats[0].ID, "cat_personales") {
		t.Fatalf("categorias = %+v, want one with generated cat_personales id", cats)
	}
}

func TestOnChangeNotFiredOnValidationError(t *testing.T) {
	s := store.NewMemory()
	calls := 0
	a, err := New(s, "test-token", func() { calls++ })
	if err != nil {
		t.Fatal(err)
	}

	rec := postCreate(t, a, url.Values{"icono": {"x"}})
	if rec.Code != http.StatusOK {
		t.Fatalf("invalid create = %d, want 200 (re-rendered form)", rec.Code)
	}
	if calls != 0 {
		t.Fatalf("onChange calls = %d, want 0", calls)
	}
}

func TestNewRejectsEmptyToken(t *testing.T) {
	if _, err := New(store.NewMemory(), "", nil); err == nil {
		t.Fatal("New with empty token = nil error, want error")
	}
}

func TestStaticCSSServed(t *testing.T) {
	a, err := New(store.NewMemory(), "test-token", nil)
	if err != nil {
		t.Fatal(err)
	}
	req := httptest.NewRequest(http.MethodGet, "/admin/static/admin.css", nil)
	rec := httptest.NewRecorder()
	a.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("GET /admin/static/admin.css = %d, want 200", rec.Code)
	}
	if ct := rec.Header().Get("Content-Type"); !strings.Contains(ct, "text/css") {
		t.Fatalf("Content-Type = %q, want text/css", ct)
	}
	if !strings.Contains(rec.Body.String(), "--panel") {
		t.Fatal("admin.css body missing expected rules")
	}
}

func TestTramiteEntidadHiddenFromFormButPreserved(t *testing.T) {
	ctx := context.Background()
	s := store.NewMemory()
	seedTramiteDeps(t, s)
	a, err := New(s, "test-token", nil)
	if err != nil {
		t.Fatal(err)
	}
	if err := s.Tramites().Save(ctx, store.Tramite{
		ID: "tra-1", CategoriaID: "cat-1", Titulo: "Pasaporte", Entidad: "Migración",
		Descripcion: "Tramitar el pasaporte", Costo: "C$ 100", TiempoEstimado: "5 días",
		Icono: "passport",
	}); err != nil {
		t.Fatal(err)
	}

	// The form must not expose an editable entidad input...
	req := httptest.NewRequest(http.MethodGet, "/admin/tramites/edit?id=tra-1", nil)
	req.Header.Set("Authorization", "Bearer test-token")
	rec := httptest.NewRecorder()
	a.ServeHTTP(rec, req)
	body := rec.Body.String()
	if strings.Contains(body, `id="f-entidad"`) {
		t.Fatal("entidad is still rendered as an editable field")
	}
	// ...but must keep the value alive for the JSON contract.
	if !strings.Contains(body, `name="entidad" value="Migración"`) {
		t.Fatal("entidad hidden input missing or empty")
	}

	// Even if the hidden input were dropped, the stored value survives.
	rec = postForm(t, a, "/admin/tramites/edit", tramiteForm("tra-1"))
	if rec.Code != http.StatusSeeOther {
		t.Fatalf("update = %d, want 303 (body %q)", rec.Code, rec.Body.String())
	}
	got, err := s.Tramites().Get(ctx, "tra-1")
	if err != nil {
		t.Fatal(err)
	}
	if got.Entidad != "Migración" {
		t.Fatalf("entidad = %q, want %q", got.Entidad, "Migración")
	}
}

func TestTramiteEntidadFollowsSingleDelegacion(t *testing.T) {
	ctx := context.Background()
	s := store.NewMemory()
	seedTramiteDeps(t, s)
	if err := s.Delegaciones().Save(ctx, store.Delegacion{
		ID: "del-2", NombreOficina: "INSS - Sucursal León", Departamento: "León",
		Latitud: 12.43, Longitud: -86.88, Direccion: "Barrio San Juan",
	}); err != nil {
		t.Fatal(err)
	}
	a, err := New(s, "test-token", nil)
	if err != nil {
		t.Fatal(err)
	}

	form := tramiteForm("tra-1")
	form.Set("link:delegacionId", "del-1")
	if rec := postForm(t, a, "/admin/tramites/new", form); rec.Code != http.StatusSeeOther {
		t.Fatalf("create = %d, want 303 (body %q)", rec.Code, rec.Body.String())
	}
	got, err := s.Tramites().Get(ctx, "tra-1")
	if err != nil {
		t.Fatal(err)
	}
	if got.Entidad != "CSE" {
		t.Fatalf("entidad = %q, want %q", got.Entidad, "CSE")
	}

	// With two delegaciones the field keeps its previous value.
	form = tramiteForm("tra-1")
	form.Add("link:delegacionId", "del-1")
	form.Add("link:delegacionId", "del-2")
	if rec := postForm(t, a, "/admin/tramites/edit", form); rec.Code != http.StatusSeeOther {
		t.Fatalf("update = %d, want 303 (body %q)", rec.Code, rec.Body.String())
	}
	got, err = s.Tramites().Get(ctx, "tra-1")
	if err != nil {
		t.Fatal(err)
	}
	if got.Entidad != "CSE" {
		t.Fatalf("entidad with two delegaciones = %q, want unchanged %q", got.Entidad, "CSE")
	}
}

func TestIDsAreHiddenFromFormsAndLists(t *testing.T) {
	s := store.NewMemory()
	seedTramiteDeps(t, s)
	a, err := New(s, "test-token", nil)
	if err != nil {
		t.Fatal(err)
	}

	req := httptest.NewRequest(http.MethodGet, "/admin/tramites/new", nil)
	req.Header.Set("Authorization", "Bearer test-token")
	rec := httptest.NewRecorder()
	a.ServeHTTP(rec, req)
	if strings.Contains(rec.Body.String(), `id="f-id"`) {
		t.Fatal("new form still exposes an editable id input")
	}

	req = httptest.NewRequest(http.MethodGet, "/admin/tramites", nil)
	req.Header.Set("Authorization", "Bearer test-token")
	rec = httptest.NewRecorder()
	a.ServeHTTP(rec, req)
	if strings.Contains(rec.Body.String(), "Ordenar por ID") {
		t.Fatal("list still renders the id column")
	}
}

func TestCreateGeneratesIDsAndLinksThem(t *testing.T) {
	ctx := context.Background()
	s := store.NewMemory()
	seedTramiteDeps(t, s)
	a, err := New(s, "test-token", nil)
	if err != nil {
		t.Fatal(err)
	}

	form := tramiteForm("")
	form.Set("link:delegacionId", "del-1")
	rec := postForm(t, a, "/admin/tramites/new", form)
	if rec.Code != http.StatusSeeOther {
		t.Fatalf("create = %d, want 303 (body %q)", rec.Code, rec.Body.String())
	}

	all, err := s.Tramites().List(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if len(all) != 1 {
		t.Fatalf("tramites = %d, want 1", len(all))
	}
	id := all[0].ID
	if !strings.HasPrefix(id, "tram_pasaporte") {
		t.Fatalf("generated id = %q, want tram_pasaporte*", id)
	}
	if all[0].Entidad != "CSE" {
		t.Fatalf("entidad = %q, want CSE", all[0].Entidad)
	}

	pairs, err := s.DelegacionTramites().List(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if len(pairs) != 1 || pairs[0].TramiteID != id || pairs[0].DelegacionID != "del-1" {
		t.Fatalf("pairs = %+v, want del-1/%s", pairs, id)
	}

	req := httptest.NewRequest(http.MethodGet, "/admin/tramites/edit?id="+id, nil)
	req.Header.Set("Authorization", "Bearer test-token")
	rec = httptest.NewRecorder()
	a.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("edit form = %d, want 200", rec.Code)
	}
	if !strings.Contains(rec.Body.String(), `value="del-1" checked`) {
		t.Fatal("generated trámite lost its delegación assignment")
	}
}
