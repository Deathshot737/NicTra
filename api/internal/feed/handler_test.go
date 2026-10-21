package feed

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"contentapi/internal/store"
)

func newTestMux(t *testing.T) (*Handler, *store.Memory, *http.ServeMux) {
	t.Helper()
	s := store.NewMemory()
	if err := store.Seed(context.Background(), s); err != nil {
		t.Fatalf("seed: %v", err)
	}
	h := NewHandler(s)
	mux := http.NewServeMux()
	h.Register(mux)
	return h, s, mux
}

func getVersion(t *testing.T, mux *http.ServeMux) (version, changedAt string) {
	t.Helper()
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/api/feed/version", nil))
	if rec.Code != http.StatusOK {
		t.Fatalf("GET /api/feed/version = %d, want 200 (body %q)", rec.Code, rec.Body.String())
	}
	var out struct {
		Version       string `json:"version"`
		ActualizadoEn string `json:"actualizadoEn"`
	}
	if err := json.Unmarshal(rec.Body.Bytes(), &out); err != nil {
		t.Fatalf("decode version response: %v", err)
	}
	return out.Version, out.ActualizadoEn
}

func TestVersionStableWithoutChanges(t *testing.T) {
	_, _, mux := newTestMux(t)
	v1, ts1 := getVersion(t, mux)
	v2, ts2 := getVersion(t, mux)
	if len(v1) != 16 {
		t.Fatalf("version = %q, want 16 hex chars", v1)
	}
	if v1 != v2 {
		t.Fatalf("version changed without writes: %s -> %s", v1, v2)
	}
	if ts1 != ts2 {
		t.Fatalf("actualizadoEn changed without writes: %s -> %s", ts1, ts2)
	}
}

func TestVersionChangedAtIsRFC3339(t *testing.T) {
	_, _, mux := newTestMux(t)
	_, ts := getVersion(t, mux)
	if _, err := time.Parse(time.RFC3339, ts); err != nil {
		t.Fatalf("actualizadoEn = %q, want RFC3339: %v", ts, err)
	}
}

func TestVersionChangesAfterWriteAndInvalidate(t *testing.T) {
	h, s, mux := newTestMux(t)
	v1, _ := getVersion(t, mux)

	if err := s.Categorias().Save(context.Background(), store.Categoria{
		ID: "cat-nueva", Nombre: "Nueva", Icono: "new",
	}); err != nil {
		t.Fatalf("save: %v", err)
	}
	if v2, _ := getVersion(t, mux); v2 != v1 {
		t.Fatalf("version changed before invalidation (TTL cache broken): %s -> %s", v1, v2)
	}

	h.InvalidateVersion()
	v3, _ := getVersion(t, mux)
	if v3 == v1 {
		t.Fatalf("version unchanged after content change: %s", v1)
	}
}

func TestFeedRejectsPost(t *testing.T) {
	_, _, mux := newTestMux(t)
	for _, path := range []string{"/api/feed", "/api/feed/version"} {
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, httptest.NewRequest(http.MethodPost, path, nil))
		if rec.Code != http.StatusMethodNotAllowed {
			t.Fatalf("POST %s = %d, want 405", path, rec.Code)
		}
	}
}
