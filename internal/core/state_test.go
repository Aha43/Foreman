package core

import (
	"fmt"
	"strings"
	"testing"
	"time"
)

func setHostname(t *testing.T, name string) {
	t.Helper()
	orig := cachedHostname
	t.Cleanup(func() { cachedHostname = orig })
	cachedHostname = name
}

func TestWindowStatus(t *testing.T) {
	setHostname(t, "testhost")
	cases := []struct {
		name string
		w    Window
		want string
	}{
		{"dead", Window{Dead: true, Command: "zsh"}, "exited"},
		{"shell", Window{Command: "zsh"}, "shell"},
		{"login shell", Window{Command: "-zsh"}, "shell"},
		{"title beats command", Window{Command: "2.1.200", Title: "✳ Claude Code"}, "✳ Claude Code"},
		{"default hostname title ignored", Window{Command: "vim", Title: "testhost"}, "vim"},
		{"blank title ignored", Window{Command: "vim", Title: "  "}, "vim"},
	}
	for _, c := range cases {
		if got := WindowStatus(c.w); got != c.want {
			t.Errorf("%s: got %q, want %q", c.name, got, c.want)
		}
	}
}

func TestWindowStatusTruncatesLongTitle(t *testing.T) {
	setHostname(t, "testhost")
	got := WindowStatus(Window{Command: "x", Title: strings.Repeat("æ", 30)})
	if r := []rune(got); len(r) != 24 || !strings.HasSuffix(got, "…") {
		t.Errorf("got %d runes %q, want 23 + ellipsis", len(r), got)
	}
}

func TestRoleName(t *testing.T) {
	if got := (Window{Role: "coder", Name: "renamed"}).RoleName(); got != "coder" {
		t.Errorf("got %q", got)
	}
	if got := (Window{Name: "fallback"}).RoleName(); got != "fallback" {
		t.Errorf("got %q", got)
	}
}

func TestHumanAge(t *testing.T) {
	ts := func(ago time.Duration) string {
		return fmt.Sprintf("%d", time.Now().Add(-ago).Unix())
	}
	cases := []struct {
		in, want string
	}{
		{ts(30 * time.Second), "now"},
		{ts(5 * time.Minute), "5m"},
		{ts(3 * time.Hour), "3h"},
		{ts(50 * time.Hour), "2d"},
		{"notanumber", "-"},
		{"0", "-"},
		{"", "-"},
	}
	for _, c := range cases {
		if got := HumanAge(c.in); got != c.want {
			t.Errorf("HumanAge(%q) = %q, want %q", c.in, got, c.want)
		}
	}
}

func TestListProjects(t *testing.T) {
	fakeTmux(t, map[string]string{
		"list-sessions": strings.Join([]string{
			"fm-B\t\t0",
			"other-session\t\t1", // not foreman-managed: filtered
			"fm-A\t1\t2",         // pinned, 2 clients
		}, "\n"),
		"list-windows": "@1\tcoder\tcoder\tzsh\t\t100\t0",
	})
	ps, err := ListProjects()
	if err != nil {
		t.Fatal(err)
	}
	if len(ps) != 2 {
		t.Fatalf("got %d projects, want 2", len(ps))
	}
	if ps[0].Name != "A" || !ps[0].Pinned || !ps[0].Attached || ps[0].Clients != 2 {
		t.Errorf("pinned project should sort first with client count: %+v", ps[0])
	}
	if ps[1].Name != "B" || ps[1].Pinned || ps[1].Attached {
		t.Errorf("project B parsed wrong: %+v", ps[1])
	}
	if len(ps[0].Windows) != 1 || ps[0].Windows[0].Role != "coder" {
		t.Errorf("windows not gathered: %+v", ps[0].Windows)
	}
}

func TestListProjectsNoServer(t *testing.T) {
	fakeTmux(t, nil) // every tmux call fails, like a missing server
	ps, err := ListProjects()
	if err != nil || ps != nil {
		t.Errorf("missing server must mean no projects, not an error: %v, %v", ps, err)
	}
}
