package feed

import (
	"encoding/json"
	"log"
	"net/http"
	"time"

	"contentapi/internal/store"
)

// Handler serves the read-only feed. It has no mutation methods by design:
// the public API is a feed only (see AGENTS.md).
type Handler struct {
	store     store.Store
	versioner *Versioner
}

func NewHandler(s store.Store) *Handler {
	return &Handler{store: s, versioner: NewVersioner(s)}
}

// InvalidateVersion forces the version cache to recompute. Wire it as the
// admin panel's onChange callback.
func (h *Handler) InvalidateVersion() { h.versioner.Invalidate() }

// Register mounts read-only feed routes.
//
//	METHOD /api/feed             -> full feed JSON
//	METHOD /api/feed/version     -> { "version", "actualizadoEn" }
func (h *Handler) Register(mux *http.ServeMux) {
	mux.HandleFunc("GET /api/feed/version", h.serveVersion)
	mux.HandleFunc("GET /api/feed", h.serveFeed)
}

// ServeHTTP handles GET/HEAD /api/feed and /api/feed/version; any other
// method is rejected 405 (read-only contract).
func (h *Handler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet && r.Method != http.MethodHead {
		w.Header().Set("Allow", "GET, HEAD")
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	if r.URL.Path == "/api/feed/version" {
		h.serveVersion(w, r)
		return
	}
	h.serveFeed(w, r)
}

// serveVersion reports the current feed content version so the app can check
// cheaply whether the feed changed before downloading it.
func (h *Handler) serveVersion(w http.ResponseWriter, r *http.Request) {
	v, changedAt, err := h.versioner.Version(r.Context())
	if err != nil {
		log.Printf("feed version error: %v", err)
		http.Error(w, "internal error", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	if err := json.NewEncoder(w).Encode(map[string]string{
		"version":       v,
		"actualizadoEn": changedAt.UTC().Format(time.RFC3339),
	}); err != nil {
		log.Printf("feed version encode error: %v", err)
	}
}

func (h *Handler) serveFeed(w http.ResponseWriter, r *http.Request) {
	start := time.Now()
	f, err := h.store.Feed(r.Context())
	if err != nil {
		log.Printf("feed error: %v", err)
		http.Error(w, "internal error", http.StatusInternalServerError)
		return
	}
	f.Normalize()

	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Cache-Control", "public, max-age=30")
	enc := json.NewEncoder(w)
	enc.SetEscapeHTML(false)
	if err := enc.Encode(f); err != nil {
		log.Printf("feed encode error: %v", err)
		return
	}
	log.Printf("feed %s %s -> %d bytes in %s", r.Method, r.URL.Path, 0, time.Since(start).Round(time.Millisecond))
}
