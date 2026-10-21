// Package data embeds the seed JSON files used to initialize the store.
package data

import "embed"

//go:embed *.json
var FS embed.FS
