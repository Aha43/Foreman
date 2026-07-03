package main

import (
	"bufio"
	"fmt"
	"os"
	"strconv"
	"strings"
	"text/tabwriter"

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
	if !core.InsideTmux() {
		fmt.Printf("  foreman %s go %s   # to jump there\n", project, role)
	}
	return nil
}

// cmdGo moves the project's view to the invoking terminal. Any other client
// showing the session is detached — one project shows in one place, so going
// somewhere never multiplies terminals (pass mirror to view alongside).
func cmdGo(project, role string, mirror bool) error {
	w, err := core.FindWindow(project, role)
	if err != nil {
		return err
	}
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
	return core.TmuxExec("attach-session", "-d", "-t", "="+core.SessionName(project))
}

func cmdAdopt(project, role string) error {
	if !core.InsideTmux() {
		return fmt.Errorf("adopt must run from inside a tmux terminal (the one to adopt)")
	}
	wid, err := core.Tmux("display-message", "-p", "#{window_id}")
	if err != nil {
		return err
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
	if _, err := core.Tmux("kill-session", "-t", "="+core.SessionName(project)); err != nil {
		return err
	}
	fmt.Printf("closed project %s\n", project)
	return nil
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
	if on {
		fmt.Printf("pinned %s\n", project)
	} else {
		fmt.Printf("unpinned %s\n", project)
	}
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
	tw := tabwriter.NewWriter(os.Stdout, 2, 4, 2, ' ', 0)
	fmt.Fprintln(tw, "TERMINAL\tRUNNING\tACTIVITY")
	for _, w := range ws {
		fmt.Fprintf(tw, "%s\t%s\t%s\n", w.RoleName(), core.WindowStatus(w), core.HumanAge(w.Activity))
	}
	return tw.Flush()
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
			parts = append(parts, fmt.Sprintf("%s:%s", w.RoleName(), core.WindowStatus(w)))
		}
		plural := "s"
		if len(p.Windows) == 1 {
			plural = ""
		}
		fmt.Fprintf(tw, "%s %s\t%d terminal%s%s\t%s\n", marker, p.Name, len(p.Windows), plural, attached, strings.Join(parts, "  "))
	}
	return tw.Flush()
}
