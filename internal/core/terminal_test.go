package core

import (
	"errors"
	"strings"
	"testing"
)

var errFake = errors.New("fake tmux failure")

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
		return []byte("boom"), errFake
	}
	runOsascript = func(args ...string) (string, error) {
		oc = append(oc, strings.Join(args, " "))
		return osaOut, nil
	}
	return &tc, &oc
}

// trackingFake builds a tmux fake serving one option listing and one
// attached-clients output — the two queries tracking code makes.
func trackingFake(listing, clients string) func(args []string) (string, bool) {
	return func(args []string) (string, bool) {
		switch {
		case args[0] == "show-options" && len(args) == 2: // the -s listing
			return listing, true
		case args[0] == "list-clients":
			return clients, true
		}
		return "", true
	}
}

func TestTermWinKey(t *testing.T) {
	if got := termWinKey("77"); got != "@fm_win_77" {
		t.Errorf("got %q", got)
	}
}

func TestOpenTerminalRecordsWindow(t *testing.T) {
	tmuxCalls, _ := recordCalls(t, trackingFake("", ""), "/dev/ttys042\n77")
	if err := OpenTerminal("'fm' 'A' go 'coder'"); err != nil {
		t.Fatal(err)
	}
	want := "set-option -s @fm_win_77 ttys042"
	if got := (*tmuxCalls)[len(*tmuxCalls)-1]; got != want {
		t.Errorf("last tmux call = %q, want %q", got, want)
	}
}

func TestOpenTerminalPurgesRecycledTty(t *testing.T) {
	// Window 60's shell already exited; its pty was recycled to the window
	// being opened. The stale entry must be closed out, not left to shadow
	// the new one in the sweep (issue #56).
	tmuxCalls, osaCalls := recordCalls(t,
		trackingFake("@fm_win_60 ttys042", ""), "/dev/ttys042\n77")
	if err := OpenTerminal("'fm' 'A' go 'coder'"); err != nil {
		t.Fatal(err)
	}
	if len(*osaCalls) != 2 || !strings.HasSuffix((*osaCalls)[1], " 60 /dev/ttys042") {
		t.Errorf("want the stale window 60 closed, got %v", *osaCalls)
	}
	joined := strings.Join(*tmuxCalls, ";")
	if !strings.Contains(joined, "set-option -s -u @fm_win_60") {
		t.Errorf("stale entry not forgotten: %s", joined)
	}
	if !strings.Contains(joined, "set-option -s @fm_win_77 ttys042") {
		t.Errorf("new window not recorded: %s", joined)
	}
}

func TestCloseTermWindow(t *testing.T) {
	tmuxCalls, osaCalls := recordCalls(t, trackingFake("@fm_win_77 ttys042", ""), "")
	CloseTermWindow("/dev/ttys042")
	if len(*osaCalls) != 1 || !strings.HasSuffix((*osaCalls)[0], " 77 /dev/ttys042") {
		t.Errorf("osascript calls = %v, want one close keyed by window id and tty", *osaCalls)
	}
	if !strings.Contains((*osaCalls)[0], "first tab of window id wid whose tty is") {
		t.Errorf("must close the tab inside the tracked window, never more: %v", *osaCalls)
	}
	if got := (*tmuxCalls)[len(*tmuxCalls)-1]; got != "set-option -s -u @fm_win_77" {
		t.Errorf("last tmux call = %q, want the unset", got)
	}
}

func TestCloseTermWindowUntrackedIsNoop(t *testing.T) {
	_, osaCalls := recordCalls(t, trackingFake("", ""), "")
	CloseTermWindow("/dev/ttys099") // listing empty: not tracked
	if len(*osaCalls) != 0 {
		t.Errorf("untracked tty must not touch Terminal, got %v", *osaCalls)
	}
}

func TestCloseTermWindowFailureKeepsTracking(t *testing.T) {
	// osascript failing (Terminal unreachable, automation denied) must not
	// unset the entry — forgetting it would strand the husk forever; kept,
	// the sweep retries (issue #56).
	tmuxCalls, _ := recordCalls(t, trackingFake("@fm_win_77 ttys042", ""), "")
	runOsascript = func(args ...string) (string, error) { return "", errFake }
	CloseTermWindow("/dev/ttys042")
	if joined := strings.Join(*tmuxCalls, ";"); strings.Contains(joined, "-u @fm_win_77") {
		t.Errorf("failed close must keep the tracking entry: %s", joined)
	}
}

func TestSweepFirstAbsenceOnlyStrikes(t *testing.T) {
	tmuxCalls, osaCalls := recordCalls(t,
		trackingFake("@fm_win_77 ttys012\nstatus-interval 15", ""), "")
	SweepTermWindows()
	if len(*osaCalls) != 0 {
		t.Errorf("first absence must not close, got %v", *osaCalls)
	}
	if !strings.Contains(strings.Join(*tmuxCalls, ";"), "set-option -s @fm_win_77 ttys012?") {
		t.Errorf("want a strike recorded, got %v", *tmuxCalls)
	}
}

func TestSweepSecondAbsenceCloses(t *testing.T) {
	tmuxCalls, osaCalls := recordCalls(t,
		trackingFake(`@fm_win_77 "ttys012?"`+"\n@fm_win_88 ttys999", "/dev/ttys999"), "")
	SweepTermWindows()
	if len(*osaCalls) != 1 || !strings.HasSuffix((*osaCalls)[0], " 77 /dev/ttys012") {
		t.Errorf("want exactly the struck orphan closed, got %v", *osaCalls)
	}
	joined := strings.Join(*tmuxCalls, ";")
	if !strings.Contains(joined, "set-option -s -u @fm_win_77") {
		t.Errorf("orphan not forgotten: %s", joined)
	}
	if strings.Contains(joined, "@fm_win_88 ttys999?") || strings.Contains(joined, "-u @fm_win_88") {
		t.Errorf("attached window must stay untouched: %s", joined)
	}
}

func TestSweepReattachClearsStrike(t *testing.T) {
	tmuxCalls, osaCalls := recordCalls(t,
		trackingFake(`@fm_win_77 "ttys012?"`, "/dev/ttys012"), "")
	SweepTermWindows()
	if len(*osaCalls) != 0 {
		t.Errorf("reattached client must not be closed, got %v", *osaCalls)
	}
	if !strings.Contains(strings.Join(*tmuxCalls, ";"), "set-option -s @fm_win_77 ttys012") {
		t.Errorf("want the strike cleared, got %v", *tmuxCalls)
	}
}

func TestSweepAbortsWhenClientQueryFails(t *testing.T) {
	_, osaCalls := recordCalls(t, func(args []string) (string, bool) {
		if args[0] == "show-options" && len(args) == 2 {
			return "@fm_win_77 ttys012", true
		}
		if args[0] == "list-clients" {
			return "", false // query FAILS — must not read as "no clients"
		}
		return "", true
	}, "")
	SweepTermWindows()
	if len(*osaCalls) != 0 {
		t.Errorf("uncertain client state must abort the sweep, got %v", *osaCalls)
	}
}

func TestSweepMigratesLegacyEntry(t *testing.T) {
	// Pre-#56 format: @fm_win_<tty> = <window id>. Rewritten id-keyed, not
	// closed — the next pass judges it with fresh strikes.
	tmuxCalls, osaCalls := recordCalls(t,
		trackingFake("@fm_win_ttys012 77", ""), "")
	SweepTermWindows()
	if len(*osaCalls) != 0 {
		t.Errorf("migration must not close anything, got %v", *osaCalls)
	}
	joined := strings.Join(*tmuxCalls, ";")
	if !strings.Contains(joined, "set-option -s -u @fm_win_ttys012") {
		t.Errorf("legacy entry not removed: %s", joined)
	}
	if !strings.Contains(joined, "set-option -s @fm_win_77 ttys012") {
		t.Errorf("legacy entry not rewritten id-keyed: %s", joined)
	}
}
