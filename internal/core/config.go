package core

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

// Config holds role presets and per-project settings, read from a minimal
// TOML subset ([section.name] headers, key = "value" pairs, # comments):
//
//	[role.planner]
//	cmd = "claude --permission-mode plan"
//
//	[project.A]
//	root = "~/code/project-a"
type Config struct {
	Roles    map[string]Role
	Projects map[string]ProjectConfig
}

type Role struct {
	Cmd string
	Dir string
}

type ProjectConfig struct {
	Root string
}

func ConfigPath() string {
	if p := os.Getenv("FOREMAN_CONFIG"); p != "" {
		return p
	}
	home, _ := os.UserHomeDir()
	return filepath.Join(home, ".config", "foreman", "config.toml")
}

func LoadConfig() Config {
	cfg := Config{Roles: map[string]Role{}, Projects: map[string]ProjectConfig{}}
	data, err := os.ReadFile(ConfigPath())
	if err != nil {
		return cfg
	}
	var section, name string
	for _, line := range strings.Split(string(data), "\n") {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		if strings.HasPrefix(line, "[") && strings.HasSuffix(line, "]") {
			parts := strings.SplitN(strings.Trim(line, "[]"), ".", 2)
			if len(parts) == 2 {
				section, name = parts[0], parts[1]
			} else {
				section, name = parts[0], ""
			}
			continue
		}
		key, val, ok := strings.Cut(line, "=")
		if !ok {
			continue
		}
		key = strings.TrimSpace(key)
		val = strings.Trim(strings.TrimSpace(val), `"`)
		switch section {
		case "role":
			r := cfg.Roles[name]
			switch key {
			case "cmd":
				r.Cmd = val
			case "dir":
				r.Dir = val
			}
			cfg.Roles[name] = r
		case "project":
			p := cfg.Projects[name]
			if key == "root" {
				p.Root = val
			}
			cfg.Projects[name] = p
		}
	}
	return cfg
}

const ConfigTemplate = `# foreman config — role presets and project settings.
#
# A role names what a terminal is for. "new <role>" starts cmd inside the
# shell (window survives the process exiting); unknown roles get a plain shell.

[role.planner]
cmd = "claude --permission-mode plan"

[role.agent]
cmd = "claude"

# [role.server]
# cmd = "npm run dev"
# dir = "~/code/project-a"        # role-specific directory, overrides project root

# Where "new" starts terminals for a project (else: your current directory).
# [project.A]
# root = "~/code/project-a"
`

// InitConfig writes a starter config for the user to edit. Later this can
// grow into an interactive Q&A; for now it scaffolds and points.
func InitConfig() (string, error) {
	path := ConfigPath()
	if _, err := os.Stat(path); err == nil {
		return "", fmt.Errorf("config already exists: %s", path)
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return "", err
	}
	if err := os.WriteFile(path, []byte(ConfigTemplate), 0o644); err != nil {
		return "", err
	}
	return path, nil
}

func ExpandHome(p string) string {
	if strings.HasPrefix(p, "~/") {
		home, _ := os.UserHomeDir()
		return filepath.Join(home, p[2:])
	}
	return p
}

// WorkDir picks the directory for a new terminal: role dir, then project
// root, then the caller's cwd.
func (c Config) WorkDir(project, role string) string {
	if d := c.Roles[role].Dir; d != "" {
		return ExpandHome(d)
	}
	if r := c.Projects[project].Root; r != "" {
		return ExpandHome(r)
	}
	wd, err := os.Getwd()
	if err != nil {
		return os.Getenv("HOME")
	}
	return wd
}
