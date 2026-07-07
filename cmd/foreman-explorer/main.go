// foreman-explorer is a macOS menu bar app over foreman's core: glance at
// projects and their participants, click one to pull its view into a
// terminal window.
package main

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"strings"
	"time"

	"fyne.io/systray"

	"github.com/arnehalvorsen/foreman/internal/core"
)

func main() {
	systray.Run(onReady, nil)
}

func onReady() {
	systray.SetTitle("fm")
	systray.SetTooltip("foreman — terminals by project")
	// All rebuilds happen on this one goroutine — the timer and manual
	// Refresh both feed it, so the done channel and the systray menu are
	// never touched concurrently (issue #31).
	go func() {
		rebuild()
		tick := time.Tick(10 * time.Second)
		for {
			select {
			case <-tick:
			case <-rebuildReq:
			}
			rebuild()
		}
	}()
}

// rebuildReq carries manual refresh requests to the rebuilder goroutine;
// capacity 1 makes extra clicks while a rebuild is pending no-ops.
var rebuildReq = make(chan struct{}, 1)

func requestRebuild() {
	select {
	case rebuildReq <- struct{}{}:
	default:
	}
}

// done is closed on every rebuild so click-listener goroutines from the
// previous menu generation exit instead of leaking. Only the rebuilder
// goroutine touches it.
var done chan struct{}

func rebuild() {
	if done != nil {
		close(done)
	}
	done = make(chan struct{})
	systray.ResetMenu()

	// Collect windows whose client ended without passing through go/done.
	core.SweepTermWindows()

	cfg := core.LoadConfig()
	projects, _ := core.ListProjects()
	if len(projects) == 0 {
		systray.AddMenuItem("no projects", "").Disable()
	}
	for _, p := range projects {
		buildProjectMenu(cfg, p, done)
		systray.AddSeparator()
	}

	listen(systray.AddMenuItem("Refresh", ""), done, requestRebuild)
	listen(systray.AddMenuItem("Quit", ""), done, systray.Quit)
}

// buildProjectMenu adds one project's block to the menu: a header opening a
// submenu of actions on the project, then one top-level item per participant
// — participants stay at the top level so the at-a-glance view and the
// one-click "pull it here" survive the actions moving in (issue #68).
func buildProjectMenu(cfg core.Config, p core.Project, d chan struct{}) {
	title := p.Name
	if p.Pinned {
		title = "★ " + title
	}
	// The header must stay enabled for its submenu to open; it has no click
	// listener, so selecting the header itself still does nothing.
	header := systray.AddMenuItem(title, "project actions")
	buildNewParticipant(cfg, p, header, d)
	for _, w := range p.Windows {
		st := watch(cfg, p, w)
		label := fmt.Sprintf("   %s%s — %s", w.RoleName(), stateMark(st), core.WindowStatus(w))
		item := systray.AddMenuItem(label, "click to open here")
		project, role := p.Name, w.RoleName()
		listen(item, d, func() { openInTerminal(project, role) })
	}
}

// buildNewParticipant fills the project's actions submenu with "New
// participant" role entries: every configured role plus shell (unknown roles
// get a plain shell, so it is always a valid choice). Roles the project
// already has are shown disabled — new would only refuse them (issue #69).
func buildNewParticipant(cfg core.Config, p core.Project, header *systray.MenuItem, d chan struct{}) {
	menu := header.AddSubMenuItem("New participant", "start a terminal in this project")
	roles := make([]string, 0, len(cfg.Roles)+1)
	for r := range cfg.Roles {
		roles = append(roles, r)
	}
	if _, ok := cfg.Roles["shell"]; !ok {
		roles = append(roles, "shell")
	}
	sort.Strings(roles)
	have := map[string]bool{}
	for _, w := range p.Windows {
		have[w.RoleName()] = true
	}
	for _, r := range roles {
		item := menu.AddSubMenuItem(r, "")
		if have[r] {
			item.Disable()
			continue
		}
		project, role := p.Name, r
		listen(item, d, func() { newParticipant(project, role) })
	}
}

// newParticipant creates the terminal (fm new without a TTY creates without
// going), then pulls it into a Terminal window — you create a terminal to
// use it. A failure surfaces as a notification; the menu refreshes either
// way so it reflects whatever actually happened.
func newParticipant(project, role string) {
	out, err := exec.Command(fmBinary(), project, "new", role).CombinedOutput()
	if err != nil {
		msg := strings.TrimSpace(string(out))
		if msg == "" {
			msg = err.Error()
		}
		notify(msg)
	} else {
		openInTerminal(project, role)
	}
	requestRebuild()
}

// listen fires fn on every click of item until this menu generation is torn
// down (d closes), so listener goroutines never outlive their menu.
func listen(item *systray.MenuItem, d chan struct{}, fn func()) {
	go func() {
		for {
			select {
			case <-item.ClickedCh:
				fn()
			case <-d:
				return
			}
		}
	}()
}

// watch is the observation epic's memory half (issue #5): compare this
// look with the last one (stored as a tmux window option), record the new
// state, and notify on a transition into waiting — pinned projects only,
// everything else stays glanceable in this menu. Runs on the single
// rebuilder goroutine, every 10s poll.
func watch(cfg core.Config, p core.Project, w core.Window) core.State {
	st := core.StateOf(cfg, w)
	last := core.LastState(w)
	if st != last {
		core.RecordState(w, st)
		// last == "" is the first look at this window: record silently.
		if last != "" && st == core.StateWaiting && p.Pinned {
			notify(fmt.Sprintf("%s is waiting in %s", w.RoleName(), p.Name))
		}
	}
	return st
}

func stateMark(s core.State) string {
	switch s {
	case core.StateWaiting:
		return "⚠"
	case core.StateDone:
		return "○"
	}
	return ""
}

// notify posts a macOS notification; the message travels as an osascript
// argument, never interpolated into the script (same rule as issue #30).
func notify(msg string) {
	exec.Command("osascript",
		"-e", "on run argv",
		"-e", `display notification (item 1 of argv) with title "foreman"`,
		"-e", "end run", msg).Run()
}

// openInTerminal opens a tracked Terminal.app window running
// "fm <project> go <role>"; with go's move semantics the participant's view
// lands there and any other viewer detaches. Tracking means foreman closes
// the window again once its view moves away (issue #43). Every shell
// argument is quoted (issue #30).
func openInTerminal(project, role string) {
	cmd := fmt.Sprintf("%s %s go %s", core.ShellQuote(fmBinary()), core.ShellQuote(project), core.ShellQuote(role))
	core.OpenTerminal(cmd)
}

// fmBinary locates the fm CLI: the sibling binary next to this executable
// (how make install lays them out), else whatever PATH resolves.
func fmBinary() string {
	if exe, err := os.Executable(); err == nil {
		if sibling := filepath.Join(filepath.Dir(exe), "fm"); exists(sibling) {
			return sibling
		}
	}
	return "fm"
}

func exists(p string) bool {
	_, err := os.Stat(p)
	return err == nil
}
