// Command server runs the content API: the read-only feed plus the
// admin panel. Configuration comes from environment variables
// (DATABASE_URL, ADMIN_TOKEN, ADDR); a .env file in the working
// directory is applied when present.
package main

import (
	"context"
	"log"
	"net/http"

	"github.com/jackc/pgx/v5/pgxpool"

	"contentapi/internal/admin"
	"contentapi/internal/config"
	"contentapi/internal/feed"
	"contentapi/internal/store"
)

func main() {
	cfg := config.Load()
	ctx := context.Background()

	st, closer, err := openStore(ctx, cfg.DatabaseURL)
	if err != nil {
		log.Fatalf("store: %v", err)
	}
	if closer != nil {
		defer closer()
	}

	fh := feed.NewHandler(st)

	token := cfg.AdminToken
	if token == "" {
		token = admin.NewToken()
		log.Printf("ADMIN_TOKEN not set — generated admin token: %s", token)
	}
	ah, err := admin.New(st, token, fh.InvalidateVersion)
	if err != nil {
		log.Fatalf("admin: %v", err)
	}

	mux := http.NewServeMux()
	fh.Register(mux)
	mux.Handle("/admin", ah)
	mux.Handle("/admin/", ah)

	log.Printf("listening on %s", cfg.Addr)
	log.Fatal(http.ListenAndServe(cfg.Addr, mux))
}

// openStore returns the backing store and an optional cleanup func.
// With no DATABASE_URL it falls back to an in-memory store with demo data.
func openStore(ctx context.Context, databaseURL string) (store.Store, func(), error) {
	if databaseURL == "" {
		log.Print("DATABASE_URL not set — using in-memory store seeded with demo data")
		s := store.NewMemory()
		if err := store.Seed(ctx, s); err != nil {
			return nil, nil, err
		}
		return s, nil, nil
	}

	pool, err := pgxpool.New(ctx, databaseURL)
	if err != nil {
		return nil, nil, err
	}
	if err := pool.Ping(ctx); err != nil {
		pool.Close()
		return nil, nil, err
	}
	if err := store.Migrate(ctx, pool); err != nil {
		pool.Close()
		return nil, nil, err
	}
	s := store.NewPostgres(pool)
	log.Print("seeding embedded data")
	if err := store.Seed(ctx, s); err != nil {
		pool.Close()
		return nil, nil, err
	}
	return s, pool.Close, nil
}
