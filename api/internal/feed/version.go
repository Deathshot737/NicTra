package feed

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"sync"
	"time"

	"contentapi/internal/store"
)

// versionTTL is how long a computed version is reused before the feed is
// rebuilt and re-hashed. Matches the feed response's Cache-Control max-age.
const versionTTL = 30 * time.Second

// Versioner computes a short content hash of the feed so clients can detect
// updates cheaply via GET /api/feed/version without downloading the feed.
type Versioner struct {
	store store.Store

	mu          sync.Mutex
	cached      string
	changedAt   time.Time
	computedAt  time.Time
	initialized bool
	valid       bool
}

func NewVersioner(s store.Store) *Versioner {
	return &Versioner{store: s}
}

// Invalidate forces recomputation on the next Version call. Wire it as the
// admin panel's onChange callback so the version flips right after a write.
func (v *Versioner) Invalidate() {
	v.mu.Lock()
	v.valid = false
	v.mu.Unlock()
}

// Version returns the current feed version (16 hex chars of a SHA-256 over
// the canonical feed JSON) and when the content last changed. changedAt is
// best-effort: it is set on the first computation after process start and on
// every observed content change.
func (v *Versioner) Version(ctx context.Context) (version string, changedAt time.Time, err error) {
	v.mu.Lock()
	defer v.mu.Unlock()

	now := time.Now()
	if v.valid && now.Sub(v.computedAt) < versionTTL {
		return v.cached, v.changedAt, nil
	}

	f, err := v.store.Feed(ctx)
	if err != nil {
		return "", time.Time{}, err
	}
	f.Normalize()
	sum, err := hashFeed(f)
	if err != nil {
		return "", time.Time{}, err
	}
	if !v.initialized || sum != v.cached {
		v.changedAt = now
		v.initialized = true
	}
	v.cached = sum
	v.computedAt = now
	v.valid = true
	return v.cached, v.changedAt, nil
}

// hashFeed hashes the canonical JSON of the feed. Feed slices are sorted by
// the store and Normalize() fills nil slices, so the bytes are deterministic
// for identical content.
func hashFeed(f *store.Feed) (string, error) {
	b, err := json.Marshal(f)
	if err != nil {
		return "", err
	}
	sum := sha256.Sum256(b)
	return hex.EncodeToString(sum[:])[:16], nil
}
