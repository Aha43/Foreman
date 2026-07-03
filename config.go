package main

import (
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
	Projects map[string]Project
}

type Role struct {
	Cmd string
	Dir string
}

type Project struct {
	Root string
}

func configPath() string {
	if p := os.Getenv("FOREMAN_CONFIG"); p != "" {
		return p
	}
	home, _ := os.UserHomeDir()
	return filepath.Join(home, ".config", "foreman", "config.toml")
}

func loadConfig() Config {
	cfg := Config{Roles: map[string]Role{}, Projects: map[string]Project{}}
	data, err := os.ReadFile(configPath())
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

func expandHome(p string) string {
	if strings.HasPrefix(p, "~/") {
		home, _ := os.UserHomeDir()
		return filepath.Join(home, p[2:])
	}
	return p
}

// workDir picks the directory for a new terminal: role dir, then project
// root, then the caller's cwd.
func (c Config) workDir(project, role string) string {
	if d := c.Roles[role].Dir; d != "" {
		return expandHome(d)
	}
	if r := c.Projects[project].Root; r != "" {
		return expandHome(r)
	}
	wd, err := os.Getwd()
	if err != nil {
		return os.Getenv("HOME")
	}
	return wd
}
