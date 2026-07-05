package core

import (
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
)

// macOS Terminal.app integration: foreman opens windows for views, tracks
// them, and closes them again once their view moves away (issue #43) — the
// fix for "soulless" [detached] windows piling up. Tracking lives in tmux
// server options (@fm_win_<window id> = tty), keyed by Terminal's window id
// because ids are unique and never reused, while ttys are recycled the
// moment a shell exits and dead tabs keep reporting their stale tty
// (issue #56). The tty value is what matches a tracked window to its tmux
// client. Only windows foreman opened are tracked, so user-opened windows
// are never touched.

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

// termWinKey maps a Terminal window id to its tracking option name.
func termWinKey(windowID string) string {
	return "@fm_win_" + windowID
}

// trackedWindows reads every tracking option: window id → tty. A trailing
// "?" on the tty is the sweep's strike marker, kept verbatim. Nil means the
// listing itself failed — callers must treat that as "unknown", not "none".
func trackedWindows() map[string]string {
	out, err := Tmux("show-options", "-s")
	if err != nil {
		return nil
	}
	m := map[string]string{}
	for _, line := range strings.Split(out, "\n") {
		name, value, ok := strings.Cut(line, " ")
		if !ok || !strings.HasPrefix(name, "@fm_win_") {
			continue
		}
		m[strings.TrimPrefix(name, "@fm_win_")] = strings.Trim(value, `"`)
	}
	return m
}

func recordTermWindow(tty, windowID string) {
	if tty == "" || windowID == "" {
		return
	}
	base := filepath.Base(tty)
	// The new window owns this tty now, so any other entry still claiming it
	// is a husk whose shell already exited (macOS recycles ptys instantly).
	// Close it here — left tracked, the recycled tty's live client would make
	// the sweep believe the husk is attached, forever (issue #56).
	for id, val := range trackedWindows() {
		if id != windowID && strings.TrimSuffix(val, "?") == base {
			closeTracked(id, base)
		}
	}
	Tmux("set-option", "-s", termWinKey(windowID), base)
}

// CloseTermWindow closes the tracked Terminal view of one client tty, if
// any, and forgets it. Unknown ttys are a no-op — that's a user's own
// window. Only the foreman *tab* is closed — the user may have added tabs
// of their own (issue #47) — and only inside its recorded window, so a
// recycled tty can never match a tab in a window foreman didn't open
// (issue #56); Terminal closes the window itself when the last tab goes.
func CloseTermWindow(tty string) {
	base := filepath.Base(tty)
	for id, val := range trackedWindows() {
		if strings.TrimSuffix(val, "?") == base {
			closeTracked(id, base)
		}
	}
}

// closeTracked closes the tty's tab inside one tracked window and forgets
// the tracking. The window already being gone, or the tab no longer being
// identifiable, count as done. A busy tab is waited out for ~2s — right
// after detach-client the tmux process is still exiting — and if it stays
// busy the user started something of their own there: the tab is left open
// (never terminate a user's process, never trigger Terminal's confirmation
// sheet) but still forgotten, since it's not a husk. When our tab is the
// window's only one — the normal case, foreman opens fresh windows — the
// *window* is closed: Terminal 2.15 rejects `close` on tab objects with
// -1708, which is how husks piled up silently before (issue #56). The
// tab-close is still attempted for windows the user added tabs to (issue
// #47); where Terminal can't do it, the tab is left to the user rather
// than retried forever. Only a failed osascript run (Terminal unreachable,
// automation denied) keeps the entry, so a later sweep retries instead of
// stranding a husk.
func closeTracked(windowID, ttyBase string) {
	_, err := runOsascript(
		"-e", "on run argv",
		"-e", `tell application "Terminal"`,
		"-e", "set wid to (item 1 of argv) as integer",
		"-e", `if not (exists window id wid) then return "gone"`,
		"-e", "try",
		"-e", "set theTab to (first tab of window id wid whose tty is (item 2 of argv))",
		"-e", "on error",
		"-e", `return "notab"`,
		"-e", "end try",
		"-e", "repeat 20 times",
		"-e", "if not (busy of theTab) then exit repeat",
		"-e", "delay 0.1",
		"-e", "end repeat",
		"-e", `if busy of theTab then return "busy"`,
		"-e", "if (count of tabs of window id wid) is 1 then",
		"-e", "close window id wid",
		"-e", `return "closed"`,
		"-e", "end if",
		"-e", "try",
		"-e", "close theTab",
		"-e", "on error",
		"-e", `return "kepttab"`,
		"-e", "end try",
		"-e", "end tell",
		"-e", `return "closed"`,
		"-e", "end run", windowID, "/dev/"+ttyBase)
	if err != nil {
		return
	}
	Tmux("set-option", "-s", "-u", termWinKey(windowID))
}

// SweepTermWindows closes tracked windows whose tmux client no longer exists
// — views that ended without passing through go/done (crashes, manual
// detach). Called from the explorer's poll. Two safety rules (issue #48):
// a failed client query aborts the sweep (uncertainty must not read as "no
// clients" and reap everything), and a window is only closed on the second
// consecutive absent observation — a "?" strike on the option value — so a
// newborn window whose client hasn't attached yet survives the race.
func SweepTermWindows() {
	tracked := trackedWindows()
	if len(tracked) == 0 {
		return
	}
	clients, err := Tmux("list-clients", "-F", "#{client_name}")
	if err != nil {
		return
	}
	attached := map[string]bool{}
	for _, c := range strings.Split(clients, "\n") {
		if c != "" {
			attached[filepath.Base(c)] = true
		}
	}
	for id, value := range tracked {
		tty := strings.TrimSuffix(value, "?")
		if _, err := strconv.Atoi(id); err != nil {
			// Pre-#56 entry, keyed the other way around (@fm_win_<tty> =
			// window id): rewrite in the id-keyed form (strike dropped —
			// costs one extra sweep cycle) and let the next pass judge it.
			Tmux("set-option", "-s", "-u", termWinKey(id))
			if _, err := strconv.Atoi(tty); err == nil {
				Tmux("set-option", "-s", termWinKey(tty), id)
			}
			continue
		}
		switch {
		case attached[tty]:
			if strings.HasSuffix(value, "?") { // back: clear the strike
				Tmux("set-option", "-s", termWinKey(id), tty)
			}
		case strings.HasSuffix(value, "?"): // second absence: really gone
			closeTracked(id, tty)
		default: // first absence: strike, close next sweep if still gone
			Tmux("set-option", "-s", termWinKey(id), value+"?")
		}
	}
}
