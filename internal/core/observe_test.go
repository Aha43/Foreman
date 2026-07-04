package core

import "testing"

func cfgWithRoleCmds(cmds map[string]string) Config {
	cfg := Config{Roles: map[string]Role{}, Projects: map[string]ProjectConfig{}}
	for name, cmd := range cmds {
		cfg.Roles[name] = Role{Cmd: cmd}
	}
	return cfg
}

func TestStateOfReliableSignals(t *testing.T) {
	cfg := cfgWithRoleCmds(map[string]string{"coder": "claude"})
	cases := []struct {
		name string
		w    Window
		want State
	}{
		{"dead pane", Window{Role: "coder", Command: "zsh", Dead: true}, StateExited},
		{"role cmd finished", Window{Role: "coder", Command: "zsh"}, StateDone},
		{"login shell counts too", Window{Role: "coder", Command: "-zsh"}, StateDone},
		{"manual terminal", Window{Role: "human", Command: "zsh"}, StateShell},
		{"unknown role at shell", Window{Role: "scratch", Command: "bash"}, StateShell},
		{"process running", Window{Role: "coder", Command: "2.1.200"}, StateWorking},
	}
	for _, c := range cases {
		if got := StateOf(cfg, c.w); got != c.want {
			t.Errorf("%s: got %q, want %q", c.name, got, c.want)
		}
	}
}

func TestStateOfDoneUsesRoleNameFallback(t *testing.T) {
	// A window without @fm_role but whose *name* matches a preset role
	// still counts as done when back at the shell.
	cfg := cfgWithRoleCmds(map[string]string{"coder": "claude"})
	if got := StateOf(cfg, Window{Name: "coder", Command: "zsh"}); got != StateDone {
		t.Errorf("got %q, want done via RoleName fallback", got)
	}
}
