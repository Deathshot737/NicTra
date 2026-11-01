package admin

import (
	"context"
	"crypto/rand"
	"crypto/subtle"
	"embed"
	"encoding/hex"
	"encoding/json"
	"errors"
	"html/template"
	"io/fs"
	"log"
	"net/http"
	"net/url"
	"strconv"
	"strings"

	"contentapi/internal/store"
)

//go:embed templates/* static/*
var assets embed.FS

const sessionCookie = "admin_session"

// Admin serves the content-management UI. Every route under /admin is a
// mutation surface; the public API (internal/feed) stays read-only.
type Admin struct {
	store    store.Store
	token    string
	onChange func()
	ents     []Entity
	tmpl     *template.Template
	mux      *http.ServeMux
}

// New builds the admin UI. onChange (optional, may be nil) is invoked after
// every successful create/update/delete — wire it to invalidate the feed
// version cache so GET /api/feed/version reflects writes immediately.
func New(s store.Store, token string, onChange func()) (*Admin, error) {
	if token == "" {
		return nil, errors.New("admin token must not be empty")
	}
	a := &Admin{
		store:    s,
		token:    token,
		onChange: onChange,
		ents:     entities(s),
	}
	funcs := template.FuncMap{
		"val":     fieldVal,
		"checked": fieldChecked,
		"sel":     fieldSelected,
		"add":     func(a, b int) int { return a + b },
		"label": func(labels map[string]string, field, value string) string {
			if labels == nil {
				return ""
			}
			return labels[field+"\x00"+value]
		},
	}
	t, err := template.New("").Funcs(funcs).ParseFS(assets, "templates/*.html")
	if err != nil {
		return nil, err
	}
	a.tmpl = t

	a.mux = http.NewServeMux()
	// static assets (the embed FS keeps them under "static/", serve that dir)
	staticFS, err := fs.Sub(assets, "static")
	if err != nil {
		return nil, err
	}
	a.mux.Handle("GET /admin/static/", http.StripPrefix("/admin/static/", http.FileServer(http.FS(staticFS))))
	// auth
	a.mux.HandleFunc("GET /admin/login", a.handleLoginPage)
	a.mux.HandleFunc("POST /admin/login", a.handleLogin)
	a.mux.HandleFunc("POST /admin/logout", a.handleLogout)
	// everything else requires auth
	a.mux.Handle("GET /admin", a.requireAuth(http.HandlerFunc(a.handleDashboard)))
	a.mux.Handle("GET /admin/", a.requireAuth(http.HandlerFunc(a.handleDispatch)))
	a.mux.Handle("POST /admin/", a.requireAuth(http.HandlerFunc(a.handleDispatch)))
	return a, nil
}

func (a *Admin) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	a.mux.ServeHTTP(w, r)
}

// --- auth ---

func (a *Admin) authed(r *http.Request) bool {
	if c, err := r.Cookie(sessionCookie); err == nil && c.Value != "" {
		return subtle.ConstantTimeCompare([]byte(c.Value), []byte(a.token)) == 1
	}
	h := r.Header.Get("Authorization")
	if strings.HasPrefix(h, "Bearer ") {
		return subtle.ConstantTimeCompare([]byte(strings.TrimPrefix(h, "Bearer ")), []byte(a.token)) == 1
	}
	return false
}

func (a *Admin) requireAuth(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !a.authed(r) {
			if r.Method == http.MethodGet {
				http.Redirect(w, r, "/admin/login", http.StatusSeeOther)
			} else {
				http.Error(w, "unauthorized", http.StatusUnauthorized)
			}
			return
		}
		next.ServeHTTP(w, r)
	})
}

func (a *Admin) handleLoginPage(w http.ResponseWriter, r *http.Request) {
	if a.authed(r) {
		http.Redirect(w, r, "/admin", http.StatusSeeOther)
		return
	}
	a.render(w, "login.html", map[string]any{"Error": r.URL.Query().Get("error")})
}

func (a *Admin) handleLogin(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		http.Error(w, "bad form", http.StatusBadRequest)
		return
	}
	got := r.FormValue("token")
	if subtle.ConstantTimeCompare([]byte(got), []byte(a.token)) != 1 {
		log.Printf("admin login failed")
		http.Redirect(w, r, "/admin/login?error=invalid+token", http.StatusSeeOther)
		return
	}
	http.SetCookie(w, &http.Cookie{
		Name:     sessionCookie,
		Value:    a.token,
		Path:     "/",
		HttpOnly: true,
		SameSite: http.SameSiteStrictMode,
		MaxAge:   8 * 60 * 60,
	})
	log.Printf("admin login ok")
	http.Redirect(w, r, "/admin", http.StatusSeeOther)
}

func (a *Admin) handleLogout(w http.ResponseWriter, r *http.Request) {
	http.SetCookie(w, &http.Cookie{Name: sessionCookie, Value: "", Path: "/", MaxAge: -1, HttpOnly: true})
	http.Redirect(w, r, "/admin/login", http.StatusSeeOther)
}

// --- routing ---

// handleDispatch routes /admin/<rest> where rest is:
//
//	""            -> dashboard
//	<slug>        -> list
//	<slug>/new    -> create form (GET) / create (POST)
//	<slug>/edit   -> edit form (GET, ?id=) / update (POST)
//	<slug>/delete -> delete (POST)
func (a *Admin) handleDispatch(w http.ResponseWriter, r *http.Request) {
	rest := strings.TrimPrefix(r.URL.Path, "/admin")
	rest = strings.Trim(rest, "/")
	if rest == "" {
		a.handleDashboard(w, r)
		return
	}
	parts := strings.Split(rest, "/")
	slug := parts[0]
	ent := entityBySlug(a.ents, slug)
	if ent == nil {
		http.NotFound(w, r)
		return
	}

	action := ""
	if len(parts) > 1 {
		action = parts[1]
	}

	switch {
	case action == "" && r.Method == http.MethodGet:
		a.handleList(w, r, ent)
	case action == "new" && r.Method == http.MethodGet:
		a.handleForm(w, r, ent, nil, false)
	case action == "new" && r.Method == http.MethodPost:
		a.handleCreate(w, r, ent)
	case action == "edit" && r.Method == http.MethodGet:
		a.handleFormFromQuery(w, r, ent)
	case action == "edit" && r.Method == http.MethodPost:
		a.handleUpdate(w, r, ent)
	case action == "delete" && r.Method == http.MethodPost:
		a.handleDelete(w, r, ent)
	default:
		http.NotFound(w, r)
	}
}

// --- handlers ---

func (a *Admin) handleDashboard(w http.ResponseWriter, r *http.Request) {
	counts := map[string]int{}
	for _, e := range a.ents {
		rows, err := e.List(r.Context())
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		counts[e.Slug] = len(rows)
	}
	a.render(w, "dashboard.html", map[string]any{
		"Title":    "Panel de administración",
		"Active":   "inicio",
		"Entities": a.ents,
		"Counts":   counts,
	})
}

func (a *Admin) handleList(w http.ResponseWriter, r *http.Request, ent *Entity) {
	rows, err := ent.List(r.Context())
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	a.render(w, "list.html", map[string]any{
		"Title":    ent.Label,
		"Active":   ent.Slug,
		"Entity":   ent,
		"Entities": a.ents,
		"Rows":     rows,
		"Labels":   a.listLabels(r, ent),
		"Flash":    r.URL.Query().Get("flash"),
		"Err":      r.URL.Query().Get("error"),
	})
}

func (a *Admin) handleFormFromQuery(w http.ResponseWriter, r *http.Request, ent *Entity) {
	if ent.IsJoin {
		http.Redirect(w, r, "/admin/"+ent.Slug, http.StatusSeeOther)
		return
	}
	id := r.URL.Query().Get("id")
	if id == "" {
		http.Redirect(w, r, "/admin/"+ent.Slug+"/new", http.StatusSeeOther)
		return
	}
	opts, oerr := a.resolveOptions(r, ent)
	item, err := ent.Get(r.Context(), id)
	if err != nil {
		a.render(w, "form.html", map[string]any{
			"Title": ent.Label, "Active": ent.Slug, "Entity": ent, "Entities": a.ents,
			"Err": "No se encontró: " + id, "Fields": ent.Fields, "Options": opts,
			"LinkGroups": a.resolveLinks(r, ent, nil),
		})
		return
	}
	_ = oerr
	a.render(w, "form.html", map[string]any{
		"Title": ent.Label, "Active": ent.Slug, "Entity": ent, "Entities": a.ents,
		"Item": item, "Fields": ent.Fields, "IsEdit": true, "Options": opts,
		"LinkGroups": a.resolveLinks(r, ent, item),
	})
}

func (a *Admin) handleForm(w http.ResponseWriter, r *http.Request, ent *Entity, item map[string]any, isEdit bool) {
	opts, _ := a.resolveOptions(r, ent)
	a.render(w, "form.html", map[string]any{
		"Title": ent.Label, "Active": ent.Slug, "Entity": ent, "Entities": a.ents,
		"Item": item, "Fields": ent.Fields, "IsEdit": isEdit, "Options": opts,
		"LinkGroups": a.resolveLinks(r, ent, item),
	})
}

// resolveOptions pre-loads <select> choices so templates never call funcs.
func (a *Admin) resolveOptions(r *http.Request, ent *Entity) (map[string][]Option, error) {
	out := map[string][]Option{}
	for _, f := range ent.Fields {
		if f.Type != "select" || f.Options == nil {
			continue
		}
		opts, err := f.Options(r.Context())
		if err != nil {
			return out, err
		}
		out[f.Name] = opts
	}
	return out, nil
}

// listLabels maps select values to their display labels so list cells can show
// "Managua - Centro" instead of an opaque FK id.
func (a *Admin) listLabels(r *http.Request, ent *Entity) map[string]string {
	out := map[string]string{}
	for _, f := range ent.Fields {
		if f.Type != "select" || f.Options == nil {
			continue
		}
		opts, err := f.Options(r.Context())
		if err != nil {
			log.Printf("admin labels %s.%s: %v", ent.Slug, f.Name, err)
			continue
		}
		for _, o := range opts {
			label := strings.TrimSpace(strings.TrimSuffix(o.Label, " ("+o.Value+")"))
			out[f.Name+"\x00"+o.Value] = label
		}
	}
	return out
}

// resolveLinks loads the checkbox choices for an entity form. On a re-rendered
// form (validation error) the submitted selections win so nothing is lost.
func (a *Admin) resolveLinks(r *http.Request, ent *Entity, item map[string]any) []LinkGroup {
	if len(ent.Links) == 0 {
		return nil
	}
	ctx := r.Context()
	submitted, hasSubmitted := submittedLinks(item)

	var pairs []store.DelegacionTramite
	if !hasSubmitted {
		all, err := a.store.DelegacionTramites().List(ctx)
		if err != nil {
			log.Printf("admin links list: %v", err)
		} else {
			pairs = all
		}
	}

	ownerID := fieldVal(item, ent.IDField)
	groups := make([]LinkGroup, 0, len(ent.Links))
	for _, l := range ent.Links {
		opts, err := l.Options(ctx)
		if err != nil {
			log.Printf("admin links options %s: %v", ent.Slug, err)
			continue
		}
		selected := map[string]bool{}
		if hasSubmitted {
			for _, v := range submitted[l.OtherKey] {
				selected[v] = true
			}
		} else if ownerID != "" {
			for _, p := range pairs {
				if pairValue(p, l.OwnerKey) == ownerID {
					selected[pairValue(p, l.OtherKey)] = true
				}
			}
		}
		g := LinkGroup{
			FieldName: "link:" + l.OtherKey,
			Title:     l.Title,
			Help:      l.Help,
			Options:   make([]LinkOption, 0, len(opts)),
		}
		for _, o := range opts {
			isSel := selected[o.Value]
			if isSel {
				g.SelectedCount++
			}
			g.Options = append(g.Options, LinkOption{
				Value: o.Value, Label: o.Label, Detail: o.Detail, Selected: isSel,
			})
		}
		groups = append(groups, g)
	}
	return groups
}

// applyLinks diffs the checked options against the join table and persists the
// change. Runs after the entity itself was saved.
func (a *Admin) applyLinks(ctx context.Context, ent *Entity, ownerID string, selections map[string][]string) error {
	if len(ent.Links) == 0 || ownerID == "" {
		return nil
	}
	js := a.store.DelegacionTramites()
	pairs, err := js.List(ctx)
	if err != nil {
		return err
	}
	for _, l := range ent.Links {
		desired := map[string]bool{}
		for _, v := range selections[l.OtherKey] {
			desired[v] = true
		}
		current := map[string]bool{}
		for _, p := range pairs {
			if pairValue(p, l.OwnerKey) != ownerID {
				continue
			}
			other := pairValue(p, l.OtherKey)
			current[other] = true
			if !desired[other] {
				if err := js.Remove(ctx, p.DelegacionID, p.TramiteID); err != nil && !errors.Is(err, store.ErrNotFound) {
					return err
				}
			}
		}
		for other := range desired {
			if current[other] {
				continue
			}
			if err := js.Add(ctx, joinPair(l, ownerID, other)); err != nil {
				return err
			}
		}
	}
	return nil
}

// removeLinksForDeleted clears join rows that pointed at a deleted trámite or
// delegación (Postgres cascades, the in-memory store does not).
func (a *Admin) removeLinksForDeleted(r *http.Request, ent *Entity, id string) {
	if id == "" || (ent.Slug != "tramites" && ent.Slug != "delegaciones") {
		return
	}
	js := a.store.DelegacionTramites()
	pairs, err := js.List(r.Context())
	if err != nil {
		log.Printf("admin unlink %s: %v", id, err)
		return
	}
	for _, p := range pairs {
		if p.DelegacionID != id && p.TramiteID != id {
			continue
		}
		if err := js.Remove(r.Context(), p.DelegacionID, p.TramiteID); err != nil && !errors.Is(err, store.ErrNotFound) {
			log.Printf("admin unlink %s: %v", id, err)
		}
	}
}

// autoID derives a readable, unique id for a new record: prefix + slug of the
// main text field ("tram_pasaporte"). Ids are internal: the admin never types
// or sees them, they only tie links together.
func (a *Admin) autoID(ctx context.Context, ent *Entity, m map[string]any) string {
	prefix := ent.IDPrefix
	if prefix == "" {
		prefix = ent.Slug
	}
	base := slugify(fieldVal(m, ent.IDSource))
	if base == "" {
		base = "registro"
	}
	if len(base) > 48 {
		base = strings.Trim(base[:48], "_")
	}
	used := map[string]bool{}
	if rows, err := ent.List(ctx); err == nil {
		for _, row := range rows {
			if id, _ := row[ent.IDField].(string); id != "" {
				used[id] = true
			}
		}
	}
	id := prefix + "_" + base
	for n := 2; used[id]; n++ {
		id = prefix + "_" + base + "_" + strconv.Itoa(n)
	}
	return id
}

var slugReplacer = strings.NewReplacer(
	"á", "a", "é", "e", "í", "i", "ó", "o", "ú", "u", "ü", "u", "ñ", "n",
)

// slugify turns "Alcaldía de León" into "alcaldia_de_leon".
func slugify(s string) string {
	s = slugReplacer.Replace(strings.ToLower(strings.TrimSpace(s)))
	var b strings.Builder
	prevSep := true
	for _, r := range s {
		if (r >= 'a' && r <= 'z') || (r >= '0' && r <= '9') {
			b.WriteRune(r)
			prevSep = false
			continue
		}
		if !prevSep {
			b.WriteByte('_')
			prevSep = true
		}
	}
	return strings.Trim(b.String(), "_")
}

// syncEntidad fills tramites.entidad from the assigned delegación when the
// trámite has exactly one, using the delegación name prefix ("CSE - Delegación
// León" → "CSE"). The field duplicates the delegación and is kept only for the
// JSON contract; with zero or several delegaciones the stored value is left
// untouched.
func (a *Admin) syncEntidad(ctx context.Context, ent *Entity, m map[string]any) {
	if ent.Slug != "tramites" {
		return
	}
	sel, _ := m["__links"].(map[string][]string)
	ids := sel["delegacionId"]
	if len(ids) != 1 || ids[0] == "" {
		return
	}
	d, err := a.store.Delegaciones().Get(ctx, ids[0])
	if err != nil {
		return
	}
	name := strings.TrimSpace(strings.SplitN(d.NombreOficina, " - ", 2)[0])
	if name != "" {
		m["entidad"] = name
	}
}

func submittedLinks(item map[string]any) (map[string][]string, bool) {
	if item == nil {
		return nil, false
	}
	v, ok := item["__links"].(map[string][]string)
	return v, ok
}

func pairValue(p store.DelegacionTramite, key string) string {
	switch key {
	case "delegacionId":
		return p.DelegacionID
	case "tramiteId":
		return p.TramiteID
	}
	return ""
}

func joinPair(l Link, ownerID, other string) store.DelegacionTramite {
	if l.OwnerKey == "delegacionId" {
		return store.DelegacionTramite{DelegacionID: ownerID, TramiteID: other}
	}
	return store.DelegacionTramite{DelegacionID: other, TramiteID: ownerID}
}

func (a *Admin) handleCreate(w http.ResponseWriter, r *http.Request, ent *Entity) {
	m, verrs, err := a.parseForm(r, ent)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	if verrs != "" {
		a.renderErr(w, r, ent, m, false, verrs)
		return
	}
	if !ent.IsJoin && ent.IDField != "" {
		if id, _ := m[ent.IDField].(string); id == "" {
			m[ent.IDField] = a.autoID(r.Context(), ent, m)
		}
	}
	a.syncEntidad(r.Context(), ent, m)
	sel, _ := m["__links"].(map[string][]string)
	if err := ent.Save(r.Context(), m); err != nil {
		a.renderErr(w, r, ent, m, false, "Error al guardar: "+err.Error())
		return
	}
	ownerID, _ := m[ent.IDField].(string)
	if err := a.applyLinks(r.Context(), ent, ownerID, sel); err != nil {
		a.notifyChange()
		http.Redirect(w, r, "/admin/"+ent.Slug+"?error="+url.QueryEscape("Guardado, pero fallaron las asignaciones: "+err.Error()), http.StatusSeeOther)
		return
	}
	a.notifyChange()
	http.Redirect(w, r, "/admin/"+ent.Slug+"?flash=Creado", http.StatusSeeOther)
}

func (a *Admin) handleUpdate(w http.ResponseWriter, r *http.Request, ent *Entity) {
	m, verrs, err := a.parseForm(r, ent)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	if verrs != "" {
		a.renderErr(w, r, ent, m, true, verrs)
		return
	}
	if !ent.IsJoin {
		if id, _ := m[ent.IDField].(string); id == "" {
			a.renderErr(w, r, ent, m, true, "No se indicó el registro a editar.")
			return
		}
	}
	// Hidden fields (kept only for the JSON contract, e.g. tramites.entidad)
	// may be absent from the posted form; restore the stored value instead of
	// wiping it on edit.
	if id, _ := m[ent.IDField].(string); id != "" && !ent.IsJoin {
		if cur, err := ent.Get(r.Context(), id); err == nil {
			for _, f := range ent.Fields {
				if !f.Hidden {
					continue
				}
				if _, ok := m[f.Name]; ok {
					continue
				}
				if v, ok := cur[f.Name]; ok {
					m[f.Name] = v
				}
			}
		}
	}
	sel, _ := m["__links"].(map[string][]string)
	a.syncEntidad(r.Context(), ent, m)
	if err := ent.Save(r.Context(), m); err != nil {
		a.renderErr(w, r, ent, m, true, "Error al guardar: "+err.Error())
		return
	}
	ownerID, _ := m[ent.IDField].(string)
	if err := a.applyLinks(r.Context(), ent, ownerID, sel); err != nil {
		a.notifyChange()
		http.Redirect(w, r, "/admin/"+ent.Slug+"?error="+url.QueryEscape("Guardado, pero fallaron las asignaciones: "+err.Error()), http.StatusSeeOther)
		return
	}
	a.notifyChange()
	http.Redirect(w, r, "/admin/"+ent.Slug+"?flash=Guardado", http.StatusSeeOther)
}

func (a *Admin) handleDelete(w http.ResponseWriter, r *http.Request, ent *Entity) {
	m, _, err := a.parseForm(r, ent)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	if !ent.IsJoin {
		if id, _ := m[ent.IDField].(string); id == "" {
			http.Redirect(w, r, "/admin/"+ent.Slug+"?error="+url.QueryEscape("No se indicó el registro a eliminar."), http.StatusSeeOther)
			return
		}
	}
	if err := ent.Delete(r.Context(), m); err != nil {
		http.Redirect(w, r, "/admin/"+ent.Slug+"?error="+url.QueryEscape("No se pudo eliminar: "+err.Error()), http.StatusSeeOther)
		return
	}
	if id, _ := m[ent.IDField].(string); id != "" {
		a.removeLinksForDeleted(r, ent, id)
	}
	a.notifyChange()
	http.Redirect(w, r, "/admin/"+ent.Slug+"?flash=Eliminado", http.StatusSeeOther)
}

func (a *Admin) notifyChange() {
	if a.onChange != nil {
		a.onChange()
	}
}

// parseForm converts form values into a map keyed by JSON contract field names.
// Returns a human-readable validation error ("" when OK).
func (a *Admin) parseForm(r *http.Request, ent *Entity) (map[string]any, string, error) {
	if err := r.ParseForm(); err != nil {
		return nil, "", err
	}
	m := map[string]any{}
	if len(ent.Links) > 0 {
		sel := map[string][]string{}
		for _, l := range ent.Links {
			sel[l.OtherKey] = append([]string(nil), r.Form["link:"+l.OtherKey]...)
		}
		m["__links"] = sel
	}
	for _, f := range ent.Fields {
		raw := r.FormValue(f.Name)
		switch f.Type {
		case "checkbox":
			m[f.Name] = raw == "on" || raw == "1" || raw == "true"
		case "number":
			if raw == "" {
				if f.Required {
					return m, "Campo requerido: " + f.Label, nil
				}
				continue
			}
			v, err := strconv.ParseFloat(raw, 64)
			if err != nil {
				return m, "Número inválido: " + f.Label, nil
			}
			m[f.Name] = v
		case "int":
			if raw == "" {
				if f.Required {
					return m, "Campo requerido: " + f.Label, nil
				}
				continue
			}
			v, err := strconv.Atoi(raw)
			if err != nil {
				return m, "Número entero inválido: " + f.Label, nil
			}
			m[f.Name] = v
		default: // text, textarea, select
			if raw == "" {
				if f.Required {
					return m, "Campo requerido: " + f.Label, nil
				}
				if f.Optional {
					m[f.Name] = nil
				}
				continue
			}
			m[f.Name] = raw
		}
	}
	// Join tables use a composite key instead of an id.
	if ent.IsJoin {
		if _, ok := m["delegacionId"]; !ok {
			return m, "Campo requerido: Delegación", nil
		}
		if _, ok := m["tramiteId"]; !ok {
			return m, "Campo requerido: Trámite", nil
		}
	}
	return m, "", nil
}

// --- rendering ---

func (a *Admin) render(w http.ResponseWriter, name string, data map[string]any) {
	if data == nil {
		data = map[string]any{}
	}
	if _, ok := data["Entities"]; !ok {
		data["Entities"] = a.ents
	}
	if _, ok := data["Active"]; !ok {
		data["Active"] = ""
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	if err := a.tmpl.ExecuteTemplate(w, name, data); err != nil {
		log.Printf("admin render %s: %v", name, err)
	}
}

func (a *Admin) renderErr(w http.ResponseWriter, r *http.Request, ent *Entity, item map[string]any, isEdit bool, msg string) {
	a.render(w, "form.html", map[string]any{
		"Title": ent.Label, "Active": ent.Slug, "Entity": ent, "Entities": a.ents,
		"Item": item, "Fields": ent.Fields, "IsEdit": isEdit, "Err": msg,
		"LinkGroups": a.resolveLinks(r, ent, item),
	})
}

// template helpers

func fieldVal(item map[string]any, key string) string {
	if item == nil {
		return ""
	}
	v, ok := item[key]
	if !ok || v == nil {
		return ""
	}
	switch t := v.(type) {
	case string:
		return t
	case float64:
		return strconv.FormatFloat(t, 'f', -1, 64)
	case bool:
		if t {
			return "true"
		}
		return "false"
	default:
		b, _ := json.Marshal(v)
		return strings.Trim(string(b), `"`)
	}
}

func fieldChecked(item map[string]any, key string) bool {
	if item == nil {
		return false
	}
	v, ok := item[key]
	if !ok {
		return false
	}
	switch t := v.(type) {
	case bool:
		return t
	case string:
		return t == "true" || t == "on"
	}
	return false
}

func fieldSelected(item map[string]any, key, val string) bool {
	return fieldVal(item, key) == val
}

// NewToken returns a random hex token (used when ADMIN_TOKEN is unset).
func NewToken() string {
	b := make([]byte, 24)
	if _, err := rand.Read(b); err != nil {
		return "insecure-dev-token"
	}
	return hex.EncodeToString(b)
}

var _ = store.ErrNotFound
