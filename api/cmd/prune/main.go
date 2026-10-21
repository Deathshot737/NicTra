// Command prune resets a persistent database to exactly the embedded seed
// dataset: it overwrites every row defined in internal/Data with the seed
// values and then deletes any row that is not in the seed (including edits and
// content created through the admin panel).
//
// It is a development/maintenance tool and never runs as part of the server.
// Configuration comes from the environment (DATABASE_URL, plus .env in the
// working directory). With no DATABASE_URL it operates on a throwaway in-memory
// store, which is only useful to smoke-test the logic.
package main

import (
	"context"
	"log"

	"github.com/jackc/pgx/v5/pgxpool"

	"contentapi/internal/config"
	"contentapi/internal/store"
)

func main() {
	cfg := config.Load()
	ctx := context.Background()

	if cfg.DatabaseURL == "" {
		log.Print("DATABASE_URL not set — running against a throwaway in-memory store")
		s := store.NewMemory()
		if err := store.SeedOverwrite(ctx, s); err != nil {
			log.Fatalf("seed: %v", err)
		}
		if err := store.Prune(ctx, s); err != nil {
			log.Fatalf("prune: %v", err)
		}
		log.Print("in-memory prune finished (nothing was persisted)")
		return
	}

	pool, err := pgxpool.New(ctx, cfg.DatabaseURL)
	if err != nil {
		log.Fatalf("connect: %v", err)
	}
	defer pool.Close()
	if err := pool.Ping(ctx); err != nil {
		log.Fatalf("ping: %v", err)
	}

	s := store.NewPostgres(pool)
	log.Print("restoring embedded data")
	if err := store.SeedOverwrite(ctx, s); err != nil {
		log.Fatalf("seed: %v", err)
	}
	log.Print("pruning rows not present in the seed")
	if err := store.Prune(ctx, s); err != nil {
		log.Fatalf("prune: %v", err)
	}
	log.Print("database reset to the seed dataset")
}
