package config

import (
	"os"
	"path/filepath"
	"testing"
)

func TestLoadDotEnv(t *testing.T) {
	path := filepath.Join(t.TempDir(), ".env")
	content := "# comment\n" +
		"TEST_DB_URL=postgres://from-file\n" +
		"TEST_TOKEN=\"s p a c e d\"\n" +
		"export TEST_EXPORTED=1\n" +
		"malformed-line\n"
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}

	t.Setenv("TEST_DB_URL", "from-real-env")
	t.Cleanup(func() {
		os.Unsetenv("TEST_TOKEN")
		os.Unsetenv("TEST_EXPORTED")
	})

	LoadDotEnv(path)

	if got := os.Getenv("TEST_DB_URL"); got != "from-real-env" {
		t.Fatalf("TEST_DB_URL = %q, want real env to win", got)
	}
	if got := os.Getenv("TEST_TOKEN"); got != "s p a c e d" {
		t.Fatalf("TEST_TOKEN = %q, want unquoted value", got)
	}
	if got := os.Getenv("TEST_EXPORTED"); got != "1" {
		t.Fatalf("TEST_EXPORTED = %q, want 1 (export prefix handled)", got)
	}
}

func TestLoadDotEnvMissingFileIsFine(t *testing.T) {
	LoadDotEnv(filepath.Join(t.TempDir(), "no-such.env"))
}

func TestLoadDefaults(t *testing.T) {
	t.Setenv("ADDR", "")
	t.Setenv("DATABASE_URL", "postgres://test")
	t.Setenv("ADMIN_TOKEN", "tok")

	cfg := Load()
	if cfg.Addr != ":80" {
		t.Fatalf("Addr = %q, want default :80", cfg.Addr)
	}
	if cfg.DatabaseURL != "postgres://test" {
		t.Fatalf("DatabaseURL = %q, want env value", cfg.DatabaseURL)
	}
	if cfg.AdminToken != "tok" {
		t.Fatalf("AdminToken = %q, want env value", cfg.AdminToken)
	}
}
