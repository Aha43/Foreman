package core

import (
	"fmt"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
)

// recordTmux substitutes tmuxRun with fn and records every call. Like
// fakeTmux, tests using it must not run in parallel.
func recordTmux(t *testing.T, fn func(args []string) (string, error)) *[][]string {
	t.Helper()
	orig := tmuxRun
	t.Cleanup(func() { tmuxRun = orig })
	var calls [][]string
	tmuxRun = func(args ...string) ([]byte, error) {
		calls = append(calls, args)
		out, err := fn(args)
		return []byte(out), err
	}
	return &calls
}

// liveSessions fakes a tmux where exactly the named sessions exist; every
// other subcommand succeeds silently.
func liveSessions(t *testing.T, names ...string) *[][]string {
	return recordTmux(t, func(args []string) (string, error) {
		if args[0] == "has-session" {
			for _, n := range names {
				if args[2] == "="+SessionName(n) {
					return "", nil
				}
			}
			return "no such session", fmt.Errorf("exit 1")
		}
		return "", nil
	})
}

func TestRenameProjectLive(t *testing.T) {
	writeConfig(t, "[project.A]\nroot = \"~/code/a\"\n")
	calls := liveSessions(t, "A")
	if err := RenameProject("A", "B"); err != nil {
		t.Fatal(err)
	}
	var renamed, restyled bool
	for _, c := range *calls {
		if c[0] == "rename-session" {
			renamed = true
			want := []string{"rename-session", "-t", "=fm-A", "fm-B"}
			if !reflect.DeepEqual(c, want) {
				t.Errorf("rename-session args = %v, want %v", c, want)
			}
		}
		if c[0] == "set-option" && len(c) >= 5 && c[3] == "@fm_project" && c[4] == "B" {
			restyled = true
		}
	}
	if !renamed {
		t.Error("rename-session not called")
	}
	if !restyled {
		t.Error("@fm_project not re-stamped with the new name")
	}
	cfg := LoadConfig()
	if got := cfg.Projects["B"].Root; got != "~/code/a" {
		t.Errorf("config after rename: project B root = %q", got)
	}
	if _, still := cfg.Projects["A"]; still {
		t.Error("config still has [project.A] after rename")
	}
}

func TestRenameProjectDormant(t *testing.T) {
	writeConfig(t, "[project.A]\nroot = \"~/code/a\"\n")
	calls := liveSessions(t) // no sessions running
	if err := RenameProject("A", "B"); err != nil {
		t.Fatal(err)
	}
	for _, c := range *calls {
		if c[0] == "rename-session" {
			t.Error("rename-session called for a dormant project")
		}
	}
	if _, ok := LoadConfig().Projects["B"]; !ok {
		t.Error("config not renamed")
	}
}

func TestRenameProjectGuards(t *testing.T) {
	t.Run("unknown project", func(t *testing.T) {
		writeConfig(t, "")
		liveSessions(t)
		if err := RenameProject("A", "B"); err == nil {
			t.Error("want error for a project that neither runs nor is configured")
		}
	})
	t.Run("target session running", func(t *testing.T) {
		writeConfig(t, "")
		liveSessions(t, "A", "B")
		if err := RenameProject("A", "B"); err == nil || !strings.Contains(err.Error(), "already running") {
			t.Errorf("want already-running error, got %v", err)
		}
	})
	t.Run("target in config", func(t *testing.T) {
		writeConfig(t, "[project.A]\nroot = \"a\"\n[project.B]\nroot = \"b\"\n")
		liveSessions(t, "A")
		if err := RenameProject("A", "B"); err == nil || !strings.Contains(err.Error(), "already exists") {
			t.Errorf("want already-exists error, got %v", err)
		}
	})
	t.Run("unaddressable characters", func(t *testing.T) {
		// tmux would accept these names but its targets split on : and .
		// — the session would be stranded (pre-release review, v0.7.0).
		writeConfig(t, "")
		liveSessions(t, "A")
		for _, bad := range []string{"my.app", "a:b", "x[1]", "a\nb"} {
			if err := RenameProject("A", bad); err == nil {
				t.Errorf("want error renaming to %q", bad)
			}
		}
	})
	t.Run("empty and same name", func(t *testing.T) {
		writeConfig(t, "")
		liveSessions(t, "A")
		if err := RenameProject("A", ""); err == nil {
			t.Error("want error for empty new name")
		}
		if err := RenameProject("A", "A"); err == nil {
			t.Error("want error for renaming to the same name")
		}
	})
}

func TestRenameProjectInConfigPreservesFile(t *testing.T) {
	content := `# top comment stays
[role.coder]
cmd = "claude"

[project.A]
root = "~/code/a"

# [project.A] commented out — untouched
`
	writeConfig(t, content)
	found, err := RenameProjectInConfig("A", "B")
	if err != nil || !found {
		t.Fatalf("found=%v err=%v", found, err)
	}
	got, err := os.ReadFile(ConfigPath())
	if err != nil {
		t.Fatal(err)
	}
	want := strings.Replace(content, "[project.A]\nroot", "[project.B]\nroot", 1)
	if string(got) != want {
		t.Errorf("config after rename:\n%s\nwant:\n%s", got, want)
	}
}

func TestRenameProjectInConfigAbsent(t *testing.T) {
	writeConfig(t, "[project.X]\nroot = \"x\"\n")
	if found, err := RenameProjectInConfig("A", "B"); found || err != nil {
		t.Errorf("absent section: found=%v err=%v", found, err)
	}
	t.Setenv("FOREMAN_CONFIG", filepath.Join(t.TempDir(), "nope.toml"))
	if found, err := RenameProjectInConfig("A", "B"); found || err != nil {
		t.Errorf("missing file: found=%v err=%v", found, err)
	}
}
