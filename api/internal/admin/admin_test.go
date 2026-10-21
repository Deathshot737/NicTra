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

func postCreate(t *testing.T, a *Admin, form url.Values) *httptest.ResponseRecorder {
	t.Helper()
	req := httptest.NewRequest(http.MethodPost, "/admin/categorias/new", strings.NewReader(form.Encode()))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.Header.Set("Authorization", "Bearer test-token")
	rec := httptest.NewRecorder()
	a.ServeHTTP(rec, req)
	return rec
}

func TestOnChangeFiresAfterCreate(t *testing.T) {
	s := store.NewMemory()
	calls := 0
	a, err := New(s, "test-token", func() { calls++ })
	if err != nil {
		t.Fatal(err)
	}

	rec := postCreate(t, a, url.Values{
		"id": {"cat-x"}, "nombre": {"X"}, "icono": {"x"},
	})
	if rec.Code != http.StatusSeeOther {
		t.Fatalf("create = %d, want 303 (body %q)", rec.Code, rec.Body.String())
	}
	if calls != 1 {
		t.Fatalf("onChange calls = %d, want 1", calls)
	}
	if _, err := s.Categorias().Get(context.Background(), "cat-x"); err != nil {
		t.Fatalf("categoria not saved: %v", err)
	}
}

func TestOnChangeNotFiredOnValidationError(t *testing.T) {
	s := store.NewMemory()
	calls := 0
	a, err := New(s, "test-token", func() { calls++ })
	if err != nil {
		t.Fatal(err)
	}

	rec := postCreate(t, a, url.Values{"nombre": {"sin id"}, "icono": {"x"}})
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
