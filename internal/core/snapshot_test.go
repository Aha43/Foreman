package core

import (
	"encoding/json"
	"fmt"
	"reflect"
	"strings"
	"testing"
)

// fakeSnapshotTmux serves one pinned project with a plain shell and a
// waiting agent, answers show-options with last, and records set-options.
func fakeSnapshotTmux(t *testing.T, last string) *[]string {
	t.Helper()
	setHostname(t, "testhost")
	orig := tmuxRun
	t.Cleanup(func() { tmuxRun = orig })
	var sets []string
	tmuxRun = func(args ...string) ([]byte, error) {
		switch args[0] {
		case "list-sessions":
			return []byte("fm-A\t1\t1\n"), nil
		case "list-windows":
			return []byte(strings.Join([]string{
				"@1\tshell\tshell\tzsh\ttesthost\t100\t0",
				"@2\tplanner\tplanner\t2.1.200\t✳ Claude Code\t200\t0",
			}, "\n") + "\n"), nil
		case "show-options":
			return []byte(last + "\n"), nil
		case "set-option":
			sets = append(sets, strings.Join(args, " "))
			return nil, nil
		}
		return []byte("unknown command"), fmt.Errorf("exit 1")
	}
	return &sets
}

func snapshotConfig() Config {
	return Config{
		Roles:    map[string]Role{"planner": {Cmd: "claude"}},
		Projects: map[string]ProjectConfig{"A": {}, "B": {}},
	}
}

func TestTakeSnapshot(t *testing.T) {
	sets := fakeSnapshotTmux(t, "working")
	s, err := TakeSnapshot(snapshotConfig(), false)
	if err != nil {
		t.Fatal(err)
	}
	want := Snapshot{
		Schema: SnapshotSchema,
		Projects: []SnapshotProject{{
			Name: "A", Pinned: true, Attached: true, Clients: 1,
			Participants: []SnapshotParticipant{
				{ID: "@1", Role: "shell", State: StateShell, Status: "shell", Activity: 100},
				{ID: "@2", Role: "planner", State: StateWaiting, Status: "✳ Claude Code", Activity: 200},
			},
		}},
		Dormant: []string{"B"},
		Roles:   []string{"planner", "shell"},
	}
	if !reflect.DeepEqual(s, want) {
		t.Errorf("got  %+v\nwant %+v", s, want)
	}
	if len(*sets) != 0 {
		t.Errorf("a plain snapshot must not record state: %v", *sets)
	}
}

// --watch reports what was recorded before and records only what changed —
// the explorer's watch pass, moved behind one CLI call (issue #97).
func TestTakeSnapshotWatch(t *testing.T) {
	sets := fakeSnapshotTmux(t, "working")
	s, err := TakeSnapshot(snapshotConfig(), true)
	if err != nil {
		t.Fatal(err)
	}
	ps := s.Projects[0].Participants
	if ps[0].Previous != StateWorking || ps[1].Previous != StateWorking {
		t.Errorf("previous states not reported: %+v", ps)
	}
	want := []string{
		"set-option -w -t @1 @fm_last_state shell",
		"set-option -w -t @2 @fm_last_state waiting",
	}
	if !reflect.DeepEqual(*sets, want) {
		t.Errorf("recorded %v, want %v", *sets, want)
	}
}

func TestTakeSnapshotWatchUnchangedRecordsNothing(t *testing.T) {
	sets := fakeSnapshotTmux(t, "waiting")
	cfg := snapshotConfig()
	cfg.Roles = map[string]Role{} // shell window stays "shell"
	if _, err := TakeSnapshot(cfg, true); err != nil {
		t.Fatal(err)
	}
	// Only the shell participant changed (waiting → shell).
	if len(*sets) != 1 || !strings.Contains((*sets)[0], "@1") {
		t.Errorf("want only @1 recorded, got %v", *sets)
	}
}

// A missing server is no projects, and the JSON must say [] — never null —
// so consumers can iterate without guarding.
func TestTakeSnapshotNoServerMarshalsEmptyLists(t *testing.T) {
	fakeTmux(t, nil)
	s, err := TakeSnapshot(Config{}, false)
	if err != nil {
		t.Fatal(err)
	}
	b, _ := json.Marshal(s)
	got := string(b)
	for _, frag := range []string{`"projects":[]`, `"dormant":[]`, `"roles":["shell"]`} {
		if !strings.Contains(got, frag) {
			t.Errorf("%s missing from %s", frag, got)
		}
	}
	if strings.Contains(got, "previous") {
		t.Errorf("previous must be omitted outside watch mode: %s", got)
	}
}

func TestRoleChoicesAlwaysOffersShell(t *testing.T) {
	cfg := Config{Roles: map[string]Role{"shell": {}, "coder": {Cmd: "x"}}}
	if got := RoleChoices(cfg); !reflect.DeepEqual(got, []string{"coder", "shell"}) {
		t.Errorf("got %v", got)
	}
}
