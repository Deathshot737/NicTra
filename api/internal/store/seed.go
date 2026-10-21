package store

import (
	"context"

	data "contentapi/internal/Data"
)

// Seed populates the store with the initial dataset embedded from
// internal/Data/*.json (full feed contract, FK-validated, idempotent). Rows that
// already exist are left untouched, so restarting the server never reverts
// content edited through the admin panel.
func Seed(ctx context.Context, s Store) error {
	return SeedFromFS(ctx, s, data.FS)
}

// SeedOverwrite restores every row defined in internal/Data/*.json, replacing
// existing rows. Used by the prune tool to reset a database to the seed dataset.
func SeedOverwrite(ctx context.Context, s Store) error {
	return SeedOverwriteFromFS(ctx, s, data.FS)
}
