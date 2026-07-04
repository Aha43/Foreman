package core

// State is what a participant is doing, observed as a snapshot — computed
// fresh from the tmux server on demand, never stored (see issue #1).
type State string

const (
	StateWorking State = "working" // agent busy
	StateWaiting State = "waiting" // agent wants attention
	StateDone    State = "done"    // role's command finished, back at the shell
	StateShell   State = "shell"   // manual terminal at its shell
	StateExited  State = "exited"  // pane dead
)

// StateOf observes one participant. The reliable signals: a dead pane, and
// "dropped back to the shell" — role commands run inside the shell, so the
// current command being a shell means the role's process ended (done) or the
// terminal never ran one (shell).
func StateOf(cfg Config, w Window) State {
	if w.Dead {
		return StateExited
	}
	if shells[w.Command] {
		if cfg.Roles[w.RoleName()].Cmd != "" {
			return StateDone
		}
		return StateShell
	}
	return StateWorking
}
