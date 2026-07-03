package main

import (
	"bufio"
	"fmt"
	"os"
	"sort"
	"strconv"
	"strings"
	"text/tabwriter"
	"time"
)

func cmdNew(project, role string) error {
	cfg := loadConfig()
	dir := cfg.workDir(project, role)
	var wid string
	var err error
	if !sessionExists(project) {
		wid, err = tmux("new-session", "-d", "-s", sessionName(project), "-n", role,
			"-c", dir, "-P", "-F", "#{window_id}")
	} else {
		if w, ferr := findWindow(project, role); ferr == nil {
			return fmt.Errorf("project %s already has a %q terminal (%s) — use: foreman %s go %s",
				project, role, w.ID, project, role)
		}
		wid, err = tmux("new-window", "-t", "="+sessionName(project), "-n", role,
			"-c", dir, "-P", "-F", "#{window_id}")
	}
	if err != nil {
		return err
	}
	tmux("set-option", "-w", "-t", wid, "@fm_role", role)
	tmux("set-option", "-w", "-t", wid, "automatic-rename", "off")
	styleSession(project)

	// Role commands run inside the shell (not as the window command) so the
	// window survives the process exiting.
	if cmd := cfg.Roles[role].Cmd; cmd != "" {
		if _, err := tmux("send-keys", "-t", wid, "-l", cmd); err != nil {
			return err
		}
		if _, err := tmux("send-keys", "-t", wid, "Enter"); err != nil {
			return err
		}
	}
	fmt.Printf("created %s/%s in %s\n", project, role, dir)
	if !insideTmux() {
		fmt.Printf("  foreman %s go %s   # to jump there\n", project, role)
	}
	return nil
}

func cmdGo(project, role string) error {
	w, err := findWindow(project, role)
	if err != nil {
		return err
	}
	if insideTmux() {
		_, err = tmux("switch-client", "-t", w.ID)
		return err
	}
	if _, err := tmux("select-window", "-t", w.ID); err != nil {
		return err
	}
	return tmuxExec("attach-session", "-t", "="+sessionName(project))
}

func cmdAdopt(project, role string) error {
	if !insideTmux() {
		return fmt.Errorf("adopt must run from inside a tmux terminal (the one to adopt)")
	}
	wid, err := tmux("display-message", "-p", "#{window_id}")
	if err != nil {
		return err
	}
	if !sessionExists(project) {
		if _, err := tmux("new-session", "-d", "-s", sessionName(project), "-n", "__fm_placeholder"); err != nil {
			return err
		}
		defer func() {
			if w, err := findWindow(project, "__fm_placeholder"); err == nil {
				tmux("kill-window", "-t", w.ID)
			}
		}()
	}
	// move-window needs an explicit free index.
	ws, err := listWindows(project)
	if err != nil {
		return err
	}
	next := 0
	for _, w := range ws {
		if out, err := tmux("display-message", "-p", "-t", w.ID, "#{window_index}"); err == nil {
			if i, err := strconv.Atoi(out); err == nil && i >= next {
				next = i + 1
			}
		}
	}
	if _, err := tmux("move-window", "-s", wid, "-t", fmt.Sprintf("=%s:%d", sessionName(project), next)); err != nil {
		return err
	}
	tmux("set-option", "-w", "-t", wid, "@fm_role", role)
	tmux("set-option", "-w", "-t", wid, "automatic-rename", "off")
	tmux("rename-window", "-t", wid, role)
	styleSession(project)
	// Follow the terminal to its new home so the user's view doesn't jump elsewhere.
	tmux("switch-client", "-t", wid)
	fmt.Printf("adopted this terminal into %s as %q\n", project, role)
	return nil
}

func cmdDone(project, role string) error {
	if role != "" {
		w, err := findWindow(project, role)
		if err != nil {
			return err
		}
		if _, err := tmux("kill-window", "-t", w.ID); err != nil {
			return err
		}
		fmt.Printf("closed %s/%s\n", project, role)
		return nil
	}
	ws, err := listWindows(project)
	if err != nil {
		return err
	}
	fmt.Printf("kill project %s with %d terminal(s)? [y/N] ", project, len(ws))
	line, _ := bufio.NewReader(os.Stdin).ReadString('\n')
	if ans := strings.ToLower(strings.TrimSpace(line)); ans != "y" && ans != "yes" {
		fmt.Println("aborted")
		return nil
	}
	if _, err := tmux("kill-session", "-t", "="+sessionName(project)); err != nil {
		return err
	}
	fmt.Printf("closed project %s\n", project)
	return nil
}

func cmdPin(project string, on bool) error {
	if !sessionExists(project) {
		return fmt.Errorf("no project %s", project)
	}
	val := ""
	if on {
		val = "1"
	}
	// set-option does not accept the "=" exact-match target prefix.
	if _, err := tmux("set-option", "-t", sessionName(project), "@fm_priority", val); err != nil {
		return err
	}
	if on {
		fmt.Printf("pinned %s\n", project)
	} else {
		fmt.Printf("unpinned %s\n", project)
	}
	return nil
}

var shells = map[string]bool{"zsh": true, "bash": true, "fish": true, "sh": true, "-zsh": true, "-bash": true}

func windowStatus(w window) string {
	if w.Dead {
		return "exited"
	}
	if shells[w.Command] {
		return "shell"
	}
	// Apps that announce themselves via the pane title (Claude Code sets
	// "✳ Claude Code") beat the process name, which can be meaningless —
	// Claude Code's native binary is named after its version ("2.1.200").
	if t := paneTitle(w); t != "" {
		return t
	}
	return w.Command
}

// paneTitle returns the pane title if an application actually set it;
// untouched panes carry the hostname as tmux's default.
func paneTitle(w window) string {
	t := strings.TrimSpace(w.Title)
	if t == "" || t == hostname() {
		return ""
	}
	if r := []rune(t); len(r) > 24 {
		t = string(r[:23]) + "…"
	}
	return t
}

var cachedHostname string

func hostname() string {
	if cachedHostname == "" {
		cachedHostname, _ = os.Hostname()
	}
	return cachedHostname
}

func humanAge(unixStr string) string {
	ts, err := strconv.ParseInt(unixStr, 10, 64)
	if err != nil || ts == 0 {
		return "-"
	}
	d := time.Since(time.Unix(ts, 0))
	switch {
	case d < time.Minute:
		return "now"
	case d < time.Hour:
		return fmt.Sprintf("%dm", int(d.Minutes()))
	case d < 24*time.Hour:
		return fmt.Sprintf("%dh", int(d.Hours()))
	default:
		return fmt.Sprintf("%dd", int(d.Hours()/24))
	}
}

func cmdList(project string) error {
	if !sessionExists(project) {
		return fmt.Errorf("no project %s", project)
	}
	ws, err := listWindows(project)
	if err != nil {
		return err
	}
	tw := tabwriter.NewWriter(os.Stdout, 2, 4, 2, ' ', 0)
	fmt.Fprintln(tw, "TERMINAL\tRUNNING\tACTIVITY")
	for _, w := range ws {
		name := w.Role
		if name == "" {
			name = w.Name
		}
		fmt.Fprintf(tw, "%s\t%s\t%s\n", name, windowStatus(w), humanAge(w.Activity))
	}
	return tw.Flush()
}

type projectInfo struct {
	Name     string
	Pinned   bool
	Attached bool
	Windows  []window
}

func cmdListAll() error {
	out, err := tmux("list-sessions", "-F", "#{session_name}\t#{@fm_priority}\t#{session_attached}")
	if err != nil {
		fmt.Println("no projects (tmux server not running)")
		return nil
	}
	var projects []projectInfo
	for _, line := range strings.Split(out, "\n") {
		f := strings.Split(line, "\t")
		if len(f) < 3 || !strings.HasPrefix(f[0], sessionPrefix) {
			continue
		}
		name := strings.TrimPrefix(f[0], sessionPrefix)
		ws, _ := listWindows(name)
		projects = append(projects, projectInfo{Name: name, Pinned: f[1] == "1", Attached: f[2] != "0", Windows: ws})
	}
	if len(projects) == 0 {
		fmt.Println("no projects")
		return nil
	}
	sort.SliceStable(projects, func(i, j int) bool {
		return projects[i].Pinned && !projects[j].Pinned
	})
	tw := tabwriter.NewWriter(os.Stdout, 2, 4, 2, ' ', 0)
	for _, p := range projects {
		marker := " "
		if p.Pinned {
			marker = "*"
		}
		attached := ""
		if p.Attached {
			attached = " (attached)"
		}
		var parts []string
		for _, w := range p.Windows {
			name := w.Role
			if name == "" {
				name = w.Name
			}
			parts = append(parts, fmt.Sprintf("%s:%s", name, windowStatus(w)))
		}
		plural := "s"
		if len(p.Windows) == 1 {
			plural = ""
		}
		fmt.Fprintf(tw, "%s %s\t%d terminal%s%s\t%s\n", marker, p.Name, len(p.Windows), plural, attached, strings.Join(parts, "  "))
	}
	return tw.Flush()
}
