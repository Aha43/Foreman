// Package core is foreman's engine: the tmux mapping (project = session,
// participant = window tagged with @fm_role), config, and state gathering.
// The CLI and the explorer GUI are thin frontends over it.
package core

import (
	"fmt"
	"os"
	"os/exec"
	"strings"
	"syscall"
)

// tmuxRun executes the tmux binary; a seam so tests can substitute canned
// output (issue #20).
var tmuxRun = func(args ...string) ([]byte, error) {
	return exec.Command("tmux", args...).CombinedOutput()
}

// Tmux runs a tmux command and returns trimmed stdout.
func Tmux(args ...string) (string, error) {
	out, err := tmuxRun(args...)
	s := strings.TrimSpace(string(out))
	if err != nil {
		if s != "" {
			return "", fmt.Errorf("tmux %s: %s", args[0], s)
		}
		return "", fmt.Errorf("tmux %s: %w", args[0], err)
	}
	return s, nil
}

// TmuxExec replaces the current process with tmux (used for attach, so the
// invoking terminal itself becomes the tmux client).
func TmuxExec(args ...string) error {
	path, err := exec.LookPath("tmux")
	if err != nil {
		return err
	}
	return syscall.Exec(path, append([]string{"tmux"}, args...), os.Environ())
}

func InsideTmux() bool {
	return os.Getenv("TMUX") != ""
}

const SessionPrefix = "fm-"

func SessionName(project string) string {
	return SessionPrefix + project
}

func SessionExists(project string) bool {
	// "=" forces exact match instead of prefix match.
	_, err := Tmux("has-session", "-t", "="+SessionName(project))
	return err == nil
}

// Window is one participant in a project.
type Window struct {
	ID       string // immutable tmux window id, e.g. @3
	Role     string // foreman's @fm_role option
	Name     string // display name
	Command  string // pane_current_command
	Title    string // pane_title, set by apps that announce themselves (e.g. Claude Code)
	Activity string // unix timestamp of last activity
	Dead     bool
}

const windowFormat = "#{window_id}\t#{@fm_role}\t#{window_name}\t#{pane_current_command}\t#{pane_title}\t#{window_activity}\t#{pane_dead}"

func ListWindows(project string) ([]Window, error) {
	out, err := Tmux("list-windows", "-t", "="+SessionName(project), "-F", windowFormat)
	if err != nil {
		return nil, err
	}
	var ws []Window
	for _, line := range strings.Split(out, "\n") {
		f := strings.Split(line, "\t")
		if len(f) < 7 {
			continue
		}
		ws = append(ws, Window{ID: f[0], Role: f[1], Name: f[2], Command: f[3], Title: f[4], Activity: f[5], Dead: f[6] == "1"})
	}
	return ws, nil
}

// RoleName is the participant's name for lookups and display: foreman's
// @fm_role metadata, falling back to the window name.
func (w Window) RoleName() string {
	if w.Role != "" {
		return w.Role
	}
	return w.Name
}

// FindWindow resolves a role to a window, preferring foreman's @fm_role
// metadata over the (renamable) window name.
func FindWindow(project, role string) (Window, error) {
	ws, err := ListWindows(project)
	if err != nil {
		return Window{}, err
	}
	for _, w := range ws {
		if w.Role == role {
			return w, nil
		}
	}
	for _, w := range ws {
		if w.Name == role {
			return w, nil
		}
	}
	var have []string
	for _, w := range ws {
		have = append(have, w.RoleName())
	}
	return Window{}, fmt.Errorf("no terminal %q in project %s (have: %s)", role, project, strings.Join(have, ", "))
}

// DetachOtherClients drops every client viewing the project except the one
// this command runs in.
func DetachOtherClients(project string) {
	self, err := Tmux("display-message", "-p", "#{client_name}")
	if err != nil {
		return
	}
	out, err := Tmux("list-clients", "-t", SessionName(project), "-F", "#{client_name}")
	if err != nil || out == "" {
		return
	}
	for _, c := range strings.Split(out, "\n") {
		if c != "" && c != self {
			Tmux("detach-client", "-t", c)
		}
	}
}

// palette for per-project identity colors; picked by name hash so a project
// keeps its color across restarts.
var palette = []string{"colour208", "colour39", "colour112", "colour170", "colour214", "colour81"}

func ProjectColor(project string) string {
	h := 0
	for _, c := range project {
		h = h*31 + int(c)
	}
	if h < 0 {
		h = -h
	}
	return palette[h%len(palette)]
}

// StyleSession makes every terminal in the project visibly labeled with the
// project name — the fix for typing into the wrong window.
func StyleSession(project string) {
	s := SessionName(project)
	color := ProjectColor(project)
	Tmux("set-option", "-t", s, "@fm_project", project)
	Tmux("set-option", "-t", s, "status-left-length", "20")
	Tmux("set-option", "-t", s, "status-left",
		fmt.Sprintf("#[fg=colour232,bg=%s,bold] %s #[default] ", color, project))
	Tmux("set-option", "-t", s, "window-status-current-format",
		fmt.Sprintf("#[fg=colour232,bg=%s] #I:#W #[default]", color))
	Tmux("set-option", "-t", s, "window-status-format", " #I:#W ")
}
