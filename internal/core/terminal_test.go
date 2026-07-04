package core

import (
	"strings"
	"testing"
)

// recorders swap both seams and log every call; restored via t.Cleanup.
func recordCalls(t *testing.T, tmuxOut func(args []string) (string, bool), osaOut string) (tmuxCalls, osaCalls *[]string) {
	t.Helper()
	var tc, oc []string
	origT, origO := tmuxRun, runOsascript
	t.Cleanup(func() { tmuxRun, runOsascript = origT, origO })
	tmuxRun = func(args ...string) ([]byte, error) {
		tc = append(tc, strings.Join(args, " "))
		if out, ok := tmuxOut(args); ok {
			return []byte(out + "\n"), nil
		}
		return []byte(""), nil
	}
	runOsascript = func(args ...string) (string, error) {
		oc = append(oc, strings.Join(args, " "))
		return osaOut, nil
	}
	return &tc, &oc
}

func TestTermWinKey(t *testing.T) {
	if got := termWinKey("/dev/ttys012"); got != "@fm_win_ttys012" {
		t.Errorf("got %q", got)
	}
	if got := termWinKey("ttys012"); got != "@fm_win_ttys012" {
		t.Errorf("bare tty: got %q", got)
	}
}

func TestOpenTerminalRecordsWindow(t *testing.T) {
	tmuxCalls, _ := recordCalls(t, func([]string) (string, bool) { return "", true }, "/dev/ttys042\n77")
	if err := OpenTerminal("'fm' 'A' go 'coder'"); err != nil {
		t.Fatal(err)
	}
	want := "set-option -s @fm_win_ttys042 77"
	if len(*tmuxCalls) != 1 || (*tmuxCalls)[0] != want {
		t.Errorf("tmux calls = %v, want [%s]", *tmuxCalls, want)
	}
}

func TestCloseTermWindow(t *testing.T) {
	tmuxCalls, osaCalls := recordCalls(t, func(args []string) (string, bool) {
		if args[0] == "show-options" {
			return "77", true
		}
		return "", true
	}, "")
	CloseTermWindow("/dev/ttys042")
	if len(*osaCalls) != 1 || !strings.HasSuffix((*osaCalls)[0], " 77") {
		t.Errorf("osascript calls = %v, want one close ending in the id", *osaCalls)
	}
	if got := (*tmuxCalls)[len(*tmuxCalls)-1]; got != "set-option -s -u @fm_win_ttys042" {
		t.Errorf("last tmux call = %q, want the unset", got)
	}
}

func TestCloseTermWindowUntrackedIsNoop(t *testing.T) {
	_, osaCalls := recordCalls(t, func(args []string) (string, bool) { return "", true }, "")
	CloseTermWindow("/dev/ttys099") // show-options returns empty: not tracked
	if len(*osaCalls) != 0 {
		t.Errorf("untracked tty must not touch Terminal, got %v", *osaCalls)
	}
}

func TestSweepClosesOnlyOrphans(t *testing.T) {
	tmuxCalls, osaCalls := recordCalls(t, func(args []string) (string, bool) {
		switch {
		case args[0] == "show-options" && len(args) == 2: // the -s listing
			return "@fm_win_ttys012 77\n@fm_win_ttys999 88\nstatus-interval 15", true
		case args[0] == "show-options": // per-key value lookup
			if strings.Contains(strings.Join(args, " "), "ttys012") {
				return "77", true
			}
			return "88", true
		case args[0] == "list-clients":
			return "/dev/ttys999", true // ttys999 still attached
		}
		return "", true
	}, "")
	SweepTermWindows()
	if len(*osaCalls) != 1 || !strings.HasSuffix((*osaCalls)[0], " 77") {
		t.Errorf("want exactly the orphan (77) closed, got %v", *osaCalls)
	}
	joined := strings.Join(*tmuxCalls, ";")
	if !strings.Contains(joined, "set-option -s -u @fm_win_ttys012") {
		t.Errorf("orphan not forgotten: %s", joined)
	}
	if strings.Contains(joined, "-u @fm_win_ttys999") {
		t.Errorf("attached window must stay tracked: %s", joined)
	}
}
