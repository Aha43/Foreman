package core

import (
	"os"
	"path/filepath"
	"testing"
)

func cfgWithRoots(roots map[string]string) Config {
	cfg := Config{Roles: map[string]Role{}, Projects: map[string]ProjectConfig{}}
	for name, root := range roots {
		cfg.Projects[name] = ProjectConfig{Root: root}
	}
	return cfg
}

func TestProjectByRoot(t *testing.T) {
	cfg := cfgWithRoots(map[string]string{
		"Outer":    "/tmp/x",
		"Inner":    "/tmp/x/a",
		"NoRoot":   "",
		"Trailing": "/tmp/y/", // Clean() normalizes
	})
	cases := []struct {
		dir, want string
	}{
		{"/tmp/x/a/sub", "Inner"}, // deepest match wins
		{"/tmp/x/a", "Inner"},     // exact root
		{"/tmp/x/other", "Outer"},
		{"/tmp/x", "Outer"},
		{"/tmp/xab", ""}, // prefix without separator boundary must NOT match
		{"/elsewhere", ""},
		{"/tmp/y/deep", "Trailing"},
	}
	for _, c := range cases {
		if got := projectByRoot(cfg, c.dir); got != c.want {
			t.Errorf("projectByRoot(%q) = %q, want %q", c.dir, got, c.want)
		}
	}
}

func TestProjectByRootExpandsHome(t *testing.T) {
	t.Setenv("HOME", "/home/test")
	cfg := cfgWithRoots(map[string]string{"P": "~/proj"})
	if got := projectByRoot(cfg, "/home/test/proj/sub"); got != "P" {
		t.Errorf("got %q, want P", got)
	}
}

func TestCurrentProjectFromTerminal(t *testing.T) {
	t.Setenv("TMUX", "/tmp/tmux-1/default,1,0")
	fakeTmux(t, map[string]string{"display-message": "NamWeb"})
	name, source, err := CurrentProject(cfgWithRoots(nil))
	if err != nil {
		t.Fatal(err)
	}
	if name != "NamWeb" || source != "terminal" {
		t.Errorf("got %q from %q", name, source)
	}
}

func TestCurrentProjectUnmanagedSessionFallsThrough(t *testing.T) {
	t.Setenv("TMUX", "/tmp/tmux-1/default,1,0")
	fakeTmux(t, map[string]string{"display-message": ""}) // unmanaged: no @fm_project
	dir := chdirTemp(t, "myproj")
	name, source, err := CurrentProject(cfgWithRoots(nil))
	if err != nil {
		t.Fatal(err)
	}
	if name != filepath.Base(dir) || source != "folder name" {
		t.Errorf("got %q from %q, want %q from folder name", name, source, filepath.Base(dir))
	}
}

func TestCurrentProjectFromConfigRoot(t *testing.T) {
	t.Setenv("TMUX", "")
	root := chdirTemp(t, "somewhere")
	sub := filepath.Join(root, "deep")
	if err := os.MkdirAll(sub, 0o755); err != nil {
		t.Fatal(err)
	}
	t.Chdir(sub)
	name, source, err := CurrentProject(cfgWithRoots(map[string]string{"Configured": root}))
	if err != nil {
		t.Fatal(err)
	}
	if name != "Configured" || source != "config root" {
		t.Errorf("got %q from %q", name, source)
	}
}

func TestCurrentProjectFromFolderName(t *testing.T) {
	t.Setenv("TMUX", "")
	dir := chdirTemp(t, "FolderProj")
	name, source, err := CurrentProject(cfgWithRoots(nil))
	if err != nil {
		t.Fatal(err)
	}
	if name != filepath.Base(dir) || source != "folder name" {
		t.Errorf("got %q from %q", name, source)
	}
}

// chdirTemp creates <tempdir>/<name>, chdirs into it, and returns the
// symlink-resolved path (macOS's /tmp is a symlink; Getwd resolves it, so
// tests must compare against the resolved form).
func chdirTemp(t *testing.T, name string) string {
	t.Helper()
	dir := filepath.Join(t.TempDir(), name)
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatal(err)
	}
	t.Chdir(dir)
	wd, err := os.Getwd()
	if err != nil {
		t.Fatal(err)
	}
	return wd
}
