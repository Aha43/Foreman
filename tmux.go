package main

import (
	"fmt"
	"os"
	"os/exec"
	"strings"
	"syscall"
)

// tmux runs a tmux command and returns trimmed stdout.
func tmux(args ...string) (string, error) {
	out, err := exec.Command("tmux", args...).CombinedOutput()
	s := strings.TrimSpace(string(out))
	if err != nil {
		if s != "" {
			return "", fmt.Errorf("tmux %s: %s", args[0], s)
		}
		return "", fmt.Errorf("tmux %s: %w", args[0], err)
	}
	return s, nil
}

// tmuxExec replaces the current process with tmux (used for attach, so the
// invoking terminal itself becomes the tmux client).
func tmuxExec(args ...string) error {
	path, err := exec.LookPath("tmux")
	if err != nil {
		return err
	}
	return syscall.Exec(path, append([]string{"tmux"}, args...), os.Environ())
}

func insideTmux() bool {
	return os.Getenv("TMUX") != ""
}

const sessionPrefix = "fm-"

func sessionName(project string) string {
	return sessionPrefix + project
}

func sessionExists(project string) bool {
	// "=" forces exact match instead of prefix match.
	_, err := tmux("has-session", "-t", "="+sessionName(project))
	return err == nil
}

// window is one participant in a project.
type window struct {
	ID       string // immutable tmux window id, e.g. @3
	Role     string // foreman's @fm_role option
	Name     string // display name
	Command  string // pane_current_command
	Activity string // unix timestamp of last activity
	Dead     bool
}

const windowFormat = "#{window_id}\t#{@fm_role}\t#{window_name}\t#{pane_current_command}\t#{window_activity}\t#{pane_dead}"

func listWindows(project string) ([]window, error) {
	out, err := tmux("list-windows", "-t", "="+sessionName(project), "-F", windowFormat)
	if err != nil {
		return nil, err
	}
	var ws []window
	for _, line := range strings.Split(out, "\n") {
		f := strings.Split(line, "\t")
		if len(f) < 6 {
			continue
		}
		ws = append(ws, window{ID: f[0], Role: f[1], Name: f[2], Command: f[3], Activity: f[4], Dead: f[5] == "1"})
	}
	return ws, nil
}

// findWindow resolves a role to a window, preferring foreman's @fm_role
// metadata over the (renamable) window name.
func findWindow(project, role string) (window, error) {
	ws, err := listWindows(project)
	if err != nil {
		return window{}, err
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
		if w.Role != "" {
			have = append(have, w.Role)
		} else {
			have = append(have, w.Name)
		}
	}
	return window{}, fmt.Errorf("no terminal %q in project %s (have: %s)", role, project, strings.Join(have, ", "))
}

// palette for per-project identity colors; picked by name hash so a project
// keeps its color across restarts.
var palette = []string{"colour208", "colour39", "colour112", "colour170", "colour214", "colour81"}

func projectColor(project string) string {
	h := 0
	for _, c := range project {
		h = h*31 + int(c)
	}
	if h < 0 {
		h = -h
	}
	return palette[h%len(palette)]
}

// styleSession makes every terminal in the project visibly labeled with the
// project name — the fix for typing into the wrong window.
func styleSession(project string) {
	s := sessionName(project)
	color := projectColor(project)
	tmux("set-option", "-t", s, "@fm_project", project)
	tmux("set-option", "-t", s, "status-left-length", "20")
	tmux("set-option", "-t", s, "status-left",
		fmt.Sprintf("#[fg=colour232,bg=%s,bold] %s #[default] ", color, project))
	tmux("set-option", "-t", s, "window-status-current-format",
		fmt.Sprintf("#[fg=colour232,bg=%s] #I:#W #[default]", color))
	tmux("set-option", "-t", s, "window-status-format", " #I:#W ")
}
