package core

import (
	"os"
	"path/filepath"
	"strings"
)

// CurrentProject infers which project a command targets when the user omits
// the name: the managed terminal we're standing in, else the configured
// project whose root contains the cwd, else the cwd's folder name. The
// source ("terminal", "config root", "folder name") is returned so the CLI
// can show what was decided.
func CurrentProject(cfg Config) (name, source string, err error) {
	if InsideTmux() {
		// @fm_project is set on every managed session (StyleSession); empty
		// means an unmanaged session, so fall through to the cwd rules.
		if p, terr := Tmux("display-message", "-p", "#{@fm_project}"); terr == nil && p != "" {
			return p, "terminal", nil
		}
	}
	wd, err := os.Getwd()
	if err != nil {
		return "", "", err
	}
	if p := projectByRoot(cfg, wd); p != "" {
		return p, "config root", nil
	}
	return filepath.Base(wd), "folder name", nil
}

// projectByRoot finds the configured project whose root contains dir; with
// nested roots the deepest match wins.
func projectByRoot(cfg Config, dir string) string {
	best, bestLen := "", 0
	for name, pc := range cfg.Projects {
		if pc.Root == "" {
			continue
		}
		root := filepath.Clean(ExpandHome(pc.Root))
		if (dir == root || strings.HasPrefix(dir, root+string(filepath.Separator))) && len(root) > bestLen {
			best, bestLen = name, len(root)
		}
	}
	return best
}
