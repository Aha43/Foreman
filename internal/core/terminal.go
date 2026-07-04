package core

import (
	"os/exec"
	"path/filepath"
	"strings"
)

// macOS Terminal.app integration: foreman opens windows for views, tracks
// them, and closes them again once their view moves away (issue #43) — the
// fix for "soulless" [detached] windows piling up. Tracking lives in tmux
// server options (@fm_win_<tty> = Terminal window id), keyed by tty because
// that's how tmux names attached clients. Only windows foreman opened are
// tracked, so user-opened windows are never touched.

// runOsascript executes osascript; a seam so tests can fake it, like tmuxRun.
var runOsascript = func(args ...string) (string, error) {
	out, err := exec.Command("osascript", args...).CombinedOutput()
	return strings.TrimSpace(string(out)), err
}

// OpenTerminal opens a Terminal.app window running shellCmd and records the
// window against its tty. The command travels as an osascript argument,
// never interpolated into the script (issue #30).
func OpenTerminal(shellCmd string) error {
	// Returns "<tty>\n<window id>" for tracking.
	out, err := runOsascript(
		"-e", "on run argv",
		"-e", `tell application "Terminal"`,
		"-e", "activate",
		"-e", "set t to do script (item 1 of argv)",
		"-e", "set w to (first window whose selected tab is t)",
		"-e", "set out to (tty of t) & linefeed & (id of w)",
		"-e", "end tell",
		"-e", "return out",
		"-e", "end run", shellCmd)
	if err != nil {
		return err
	}
	if lines := strings.Split(out, "\n"); len(lines) >= 2 {
		recordTermWindow(strings.TrimSpace(lines[0]), strings.TrimSpace(lines[1]))
	}
	return nil
}

// termWinKey maps a client tty (/dev/ttys012) to its tracking option name.
func termWinKey(tty string) string {
	return "@fm_win_" + filepath.Base(tty)
}

func recordTermWindow(tty, windowID string) {
	if tty == "" || windowID == "" {
		return
	}
	Tmux("set-option", "-s", termWinKey(tty), windowID)
}

// CloseTermWindow closes the tracked Terminal view of one client tty, if
// any, and forgets it. Unknown ttys are a no-op — that's a user's own window.
// Only the foreman *tab* is closed, identified by its tty — the user may
// have added tabs of their own to the window (issue #47); Terminal closes
// the window itself when the last tab goes.
func CloseTermWindow(tty string) {
	key := termWinKey(tty)
	id, err := Tmux("show-options", "-s", "-q", "-v", key)
	if err != nil || id == "" {
		return
	}
	runOsascript(
		"-e", "on run argv",
		"-e", `tell application "Terminal"`,
		"-e", "repeat with w in windows",
		"-e", "try",
		"-e", "close (first tab of w whose tty is (item 1 of argv))",
		"-e", "end try",
		"-e", "end repeat",
		"-e", "end tell",
		"-e", "end run", tty)
	Tmux("set-option", "-s", "-u", key)
}

// SweepTermWindows closes tracked windows whose tmux client no longer exists
// — views that ended without passing through go/done (crashes, manual
// detach). Called from the explorer's poll.
func SweepTermWindows() {
	out, err := Tmux("show-options", "-s")
	if err != nil {
		return
	}
	clients, _ := Tmux("list-clients", "-F", "#{client_name}")
	attached := map[string]bool{}
	for _, c := range strings.Split(clients, "\n") {
		if c != "" {
			attached[filepath.Base(c)] = true
		}
	}
	for _, line := range strings.Split(out, "\n") {
		name, _, ok := strings.Cut(line, " ")
		if !ok || !strings.HasPrefix(name, "@fm_win_") {
			continue
		}
		if tty := strings.TrimPrefix(name, "@fm_win_"); !attached[tty] {
			CloseTermWindow("/dev/" + tty)
		}
	}
}
