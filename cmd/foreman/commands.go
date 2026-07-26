package main

import (
	"bufio"
	"fmt"
	"os"
	"os/exec"
	"strconv"
	"strings"
	"syscall"
	"text/tabwriter"

	"golang.org/x/term"

	"github.com/arnehalvorsen/foreman/internal/core"
)

func cmdInit() error {
	path, err := core.InitConfig()
	if err != nil {
		return err
	}
	fmt.Printf("wrote %s\nEdit it to define your roles and project roots.\n", path)
	return nil
}

func cmdNew(project, role string) error {
	cfg := core.LoadConfig()
	dir := cfg.WorkDir(project, role)
	var wid string
	var err error
	if !core.SessionExists(project) {
		wid, err = core.Tmux("new-session", "-d", "-s", core.SessionName(project), "-n", role,
			"-c", dir, "-P", "-F", "#{window_id}")
	} else {
		if w, ferr := core.FindWindow(project, role); ferr == nil {
			return fmt.Errorf("project %s already has a %q terminal (%s) — use: foreman %s go %s",
				project, role, w.ID, project, role)
		}
		wid, err = core.Tmux("new-window", "-t", "="+core.SessionName(project), "-n", role,
			"-c", dir, "-P", "-F", "#{window_id}")
	}
	if err != nil {
		return err
	}
	core.Tmux("set-option", "-w", "-t", wid, "@fm_role", role)
	core.Tmux("set-option", "-w", "-t", wid, "automatic-rename", "off")
	core.StyleSession(project)

	// Role commands run inside the shell (not as the window command) so the
	// window survives the process exiting.
	if cmd := cfg.Roles[role].Cmd; cmd != "" {
		if _, err := core.Tmux("send-keys", "-t", wid, "-l", cmd); err != nil {
			return err
		}
		if _, err := core.Tmux("send-keys", "-t", wid, "Enter"); err != nil {
			return err
		}
	}
	fmt.Printf("created %s/%s in %s\n", project, role, dir)
	// Only unknown roles get the notice — a role deliberately configured
	// without a cmd (a named plain shell) already explained itself.
	if _, known := cfg.Roles[role]; !known {
		fmt.Printf("note: role %q has no cmd in %s — opened a plain shell\n", role, core.ConfigPath())
	}
	// The typical next step is go, so offer it. Only on a TTY — scripted
	// use creates without going, as before.
	if stdinIsTTY() {
		fmt.Printf("go to %s? [Y/n] ", role)
		line, err := bufio.NewReader(os.Stdin).ReadString('\n')
		if err != nil {
			// EOF or a broken stdin is not an answer — never go on it.
			fmt.Println()
			return nil
		}
		if ans := strings.ToLower(strings.TrimSpace(line)); ans == "" || ans == "y" || ans == "yes" {
			return cmdGo(project, role, false)
		}
		return nil
	}
	if !core.InsideTmux() {
		fmt.Printf("  foreman %s go %s   # to jump there\n", project, role)
	}
	return nil
}

func stdinIsTTY() bool {
	// A real terminal check — /dev/null is a character device too, so the
	// ModeCharDevice heuristic misfires on scripted use (issue #28).
	return term.IsTerminal(int(os.Stdin.Fd()))
}

// cmdGo moves the project's view to the invoking terminal. Any other client
// showing the session is detached — one project shows in one place, so going
// somewhere never multiplies terminals (pass mirror to view alongside).
func cmdGo(project, role string, mirror bool) error {
	w, err := core.FindWindow(project, role)
	if err != nil {
		return err
	}
	// Re-stamp styling on the most-used verb so sessions self-heal — e.g.
	// the ticker's baked binary path after a moved install (issue #37).
	core.StyleSession(project)
	if core.InsideTmux() {
		if _, err := core.Tmux("switch-client", "-t", w.ID); err != nil {
			return err
		}
		if !mirror {
			core.DetachOtherClients(project)
		}
		return nil
	}
	if _, err := core.Tmux("select-window", "-t", w.ID); err != nil {
		return err
	}
	if mirror {
		return core.TmuxExec("attach-session", "-t", "="+core.SessionName(project))
	}
	// Detach current viewers ourselves (closing their foreman-opened
	// windows) before exec replaces this process; -d stays as a stragglers
	// net for clients attaching in the gap.
	core.DetachViewers(project)
	return core.TmuxExec("attach-session", "-d", "-t", "="+core.SessionName(project))
}

// cmdGoWindow opens the participant's view in a new Terminal window instead
// of switching in place (issue #6). The go running inside the new window
// does the actual move; tracking (issue #43) means the window is closed
// again once its view later moves away.
func cmdGoWindow(project, role string) error {
	if _, err := core.FindWindow(project, role); err != nil {
		return err
	}
	bin := core.BinaryPath()
	if bin == "" {
		return fmt.Errorf("cannot locate the foreman binary")
	}
	return core.OpenTerminal(fmt.Sprintf("%s %s go %s",
		core.ShellQuote(bin), core.ShellQuote(project), core.ShellQuote(role)))
}

func cmdAdopt(project, role string) error {
	if !core.InsideTmux() {
		return fmt.Errorf("adopt must run from inside a tmux terminal (the one to adopt)")
	}
	wid, err := core.Tmux("display-message", "-p", "#{window_id}")
	if err != nil {
		return err
	}
	// Same guard as new: a role is an identity — two windows with the same
	// @fm_role would make go/done resolve arbitrarily.
	if core.SessionExists(project) {
		if w, ferr := core.FindWindow(project, role); ferr == nil {
			if w.ID == wid {
				return fmt.Errorf("this terminal is already %s/%s", project, role)
			}
			return fmt.Errorf("project %s already has a %q terminal (%s) — pick another role name",
				project, role, w.ID)
		}
	}
	if !core.SessionExists(project) {
		if _, err := core.Tmux("new-session", "-d", "-s", core.SessionName(project), "-n", "__fm_placeholder"); err != nil {
			return err
		}
		defer func() {
			if w, err := core.FindWindow(project, "__fm_placeholder"); err == nil {
				core.Tmux("kill-window", "-t", w.ID)
			}
		}()
	}
	// move-window needs an explicit free index.
	ws, err := core.ListWindows(project)
	if err != nil {
		return err
	}
	next := 0
	for _, w := range ws {
		if out, err := core.Tmux("display-message", "-p", "-t", w.ID, "#{window_index}"); err == nil {
			if i, err := strconv.Atoi(out); err == nil && i >= next {
				next = i + 1
			}
		}
	}
	if _, err := core.Tmux("move-window", "-s", wid, "-t", fmt.Sprintf("=%s:%d", core.SessionName(project), next)); err != nil {
		return err
	}
	core.Tmux("set-option", "-w", "-t", wid, "@fm_role", role)
	core.Tmux("set-option", "-w", "-t", wid, "automatic-rename", "off")
	core.Tmux("rename-window", "-t", wid, role)
	core.StyleSession(project)
	// Follow the terminal to its new home so the user's view doesn't jump elsewhere.
	core.Tmux("switch-client", "-t", wid)
	fmt.Printf("adopted this terminal into %s as %q\n", project, role)
	return nil
}

func cmdDone(project, role string) error {
	if role != "" {
		w, err := core.FindWindow(project, role)
		if err != nil {
			return err
		}
		if _, err := core.Tmux("kill-window", "-t", w.ID); err != nil {
			return err
		}
		fmt.Printf("closed %s/%s\n", project, role)
		return nil
	}
	ws, err := core.ListWindows(project)
	if err != nil {
		return err
	}
	fmt.Printf("kill project %s with %d terminal(s)? [y/N] ", project, len(ws))
	line, _ := bufio.NewReader(os.Stdin).ReadString('\n')
	if ans := strings.ToLower(strings.TrimSpace(line)); ans != "y" && ans != "yes" {
		fmt.Println("aborted")
		return nil
	}
	// Remember who was viewing: their foreman-opened windows become husks
	// the moment the session dies (issue #43). The cleanup must survive this
	// very process — run from inside the project, kill-session takes our own
	// pane with it — so a detached worker does the closing (issue #46).
	viewers, _ := core.Tmux("list-clients", "-t", core.SessionName(project), "-F", "#{client_name}")
	spawnCloseWorker(strings.Split(viewers, "\n"))
	if _, err := core.Tmux("kill-session", "-t", "="+core.SessionName(project)); err != nil {
		return err
	}
	fmt.Printf("closed project %s\n", project)
	return nil
}

// spawnCloseWorker starts a detached __closewins process (its own session,
// no inherited stdio) so tracked-window cleanup survives kill-session taking
// this process down with the project (issue #46).
func spawnCloseWorker(ttys []string) {
	bin := core.BinaryPath()
	if bin == "" {
		return
	}
	args := []string{"__closewins"}
	for _, t := range ttys {
		if t != "" {
			args = append(args, t)
		}
	}
	if len(args) == 1 {
		return
	}
	cmd := exec.Command(bin, args...)
	cmd.SysProcAttr = &syscall.SysProcAttr{Setsid: true}
	cmd.Start()
}

func cmdPin(project string, on bool) error {
	if !core.SessionExists(project) {
		return fmt.Errorf("no project %s", project)
	}
	val := ""
	if on {
		val = "1"
	}
	// set-option does not accept the "=" exact-match target prefix.
	if _, err := core.Tmux("set-option", "-t", core.SessionName(project), "@fm_priority", val); err != nil {
		return err
	}
	core.StyleSession(project)
	if on {
		fmt.Printf("pinned %s\n", project)
	} else {
		fmt.Printf("unpinned %s\n", project)
	}
	return nil
}

func cmdRename(project, newName string) error {
	// Verbs are reserved as project names (see projectVerbs) — a project
	// named "done" could never be addressed with the name omitted.
	if projectVerbs[newName] || newName == "list" || newName == "init" || newName == "help" {
		return fmt.Errorf("%q is a foreman verb — reserved as a project name", newName)
	}
	if err := core.RenameProject(project, newName); err != nil {
		return err
	}
	fmt.Printf("renamed %s to %s\n", project, newName)
	return nil
}

func cmdList(project string) error {
	if !core.SessionExists(project) {
		return fmt.Errorf("no project %s", project)
	}
	ws, err := core.ListWindows(project)
	if err != nil {
		return err
	}
	cfg := core.LoadConfig()
	tw := tabwriter.NewWriter(os.Stdout, 2, 4, 2, ' ', 0)
	fmt.Fprintln(tw, "TERMINAL\tSTATE\tRUNNING\tACTIVITY")
	for _, w := range ws {
		st := core.StateOf(cfg, w)
		fmt.Fprintf(tw, "%s\t%s%s\t%s\t%s\n",
			w.RoleName(), st, stateMark(st), core.WindowStatus(w), core.HumanAge(w.Activity))
	}
	return tw.Flush()
}

// stateMark flags the states that want a glance; working stays quiet.
func stateMark(s core.State) string {
	switch s {
	case core.StateWaiting:
		return "⚠"
	case core.StateDone:
		return "○"
	}
	return ""
}

// cmdTicker prints the cross-project attention line for one status bar.
// Runs on tmux's redraw schedule, so it must stay quiet and cheap.
func cmdTicker(current string) error {
	projects, err := core.ListProjects()
	if err != nil || len(projects) == 0 {
		return nil
	}
	fmt.Print(core.TickerLine(core.LoadConfig(), projects, current))
	return nil
}

func cmdListAll() error {
	projects, err := core.ListProjects()
	if err != nil {
		return err
	}
	if len(projects) == 0 {
		fmt.Println("no projects")
		return nil
	}
	cfg := core.LoadConfig()
	tw := tabwriter.NewWriter(os.Stdout, 2, 4, 2, ' ', 0)
	for _, p := range projects {
		marker := " "
		if p.Pinned {
			marker = "*"
		}
		attached := ""
		if p.Attached {
			attached = " (attached)"
			if p.Clients > 1 {
				attached = fmt.Sprintf(" (attached ×%d)", p.Clients)
			}
		}
		var parts []string
		for _, w := range p.Windows {
			parts = append(parts, fmt.Sprintf("%s%s:%s",
				w.RoleName(), stateMark(core.StateOf(cfg, w)), core.WindowStatus(w)))
		}
		plural := "s"
		if len(p.Windows) == 1 {
			plural = ""
		}
		fmt.Fprintf(tw, "%s %s\t%d terminal%s%s\t%s\n", marker, p.Name, len(p.Windows), plural, attached, strings.Join(parts, "  "))
	}
	return tw.Flush()
}
