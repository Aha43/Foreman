package core

import (
	"fmt"
	"strings"
	"unicode"
)

// RenameProject renames a project everywhere its name lives: the tmux
// session (when running) and the [project.name] config section (when
// configured). Session and window options (@fm_priority, @fm_role, state
// memory) ride along with the session, but the badge text, the name-derived
// color, the ticker argument and @fm_project all embed the name, so styling
// is re-stamped. Renaming a dormant project is purely the config edit.
func RenameProject(old, newName string) error {
	if newName == "" {
		return fmt.Errorf("new name is empty")
	}
	if newName == old {
		return fmt.Errorf("%s is already named %s", old, newName)
	}
	// tmux accepts ':' and '.' in a session name but its targets split on
	// them — rename-session would succeed and the session could never be
	// addressed again. '[' and ']' would corrupt the config section header,
	// control characters the line-based tmux output foreman parses.
	if strings.ContainsAny(newName, ".:[]") || strings.ContainsFunc(newName, unicode.IsControl) {
		return fmt.Errorf("project name %q won't work: no . : [ ] or control characters", newName)
	}
	live := SessionExists(old)
	cfg := LoadConfig()
	_, configured := cfg.Projects[old]
	if !live && !configured {
		return fmt.Errorf("no project %s", old)
	}
	if SessionExists(newName) {
		return fmt.Errorf("a project %s is already running", newName)
	}
	if _, taken := cfg.Projects[newName]; taken {
		return fmt.Errorf("project %s already exists in %s", newName, ConfigPath())
	}
	if live {
		if _, err := Tmux("rename-session", "-t", "="+SessionName(old), SessionName(newName)); err != nil {
			return err
		}
		StyleSession(newName)
	}
	if _, err := RenameProjectInConfig(old, newName); err != nil {
		if live {
			return fmt.Errorf("session renamed, but updating %s failed: %w", ConfigPath(), err)
		}
		return err
	}
	return nil
}
