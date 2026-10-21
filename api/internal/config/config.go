package config

import (
	"bufio"
	"os"
	"strings"
)

// Config holds runtime settings extracted from environment variables.
// Values are read once at startup; .env is applied first if present
// (real environment variables always take precedence).
type Config struct {
	Addr        string // HTTP listen address (ADDR), default ":80"
	DatabaseURL string // Postgres DSN (DATABASE_URL); empty = in-memory demo store
	AdminToken  string // admin panel token (ADMIN_TOKEN); empty = auto-generated
}

// Load extracts the configuration from the process environment,
// applying .env in the working directory when present.
func Load() Config {
	LoadDotEnv(".env")
	addr := os.Getenv("ADDR")
	if addr == "" {
		addr = ":80"
	}
	return Config{
		Addr:        addr,
		DatabaseURL: os.Getenv("DATABASE_URL"),
		AdminToken:  os.Getenv("ADMIN_TOKEN"),
	}
}

// LoadDotEnv applies KEY=VALUE lines from path without overriding variables
// already set in the environment. A missing file is not an error.
func LoadDotEnv(path string) {
	f, err := os.Open(path)
	if err != nil {
		return
	}
	defer f.Close()

	sc := bufio.NewScanner(f)
	for sc.Scan() {
		line := strings.TrimSpace(sc.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		line = strings.TrimPrefix(line, "export ")
		k, v, ok := strings.Cut(line, "=")
		if !ok {
			continue
		}
		k = strings.TrimSpace(k)
		v = strings.TrimSpace(v)
		if len(v) >= 2 {
			if (v[0] == '"' && v[len(v)-1] == '"') || (v[0] == '\'' && v[len(v)-1] == '\'') {
				v = v[1 : len(v)-1]
			}
		}
		if k == "" || os.Getenv(k) != "" {
			continue
		}
		os.Setenv(k, v)
	}
}
