// foreman-explorer is a macOS menu bar app over foreman's core: glance at
// projects and their participants, click one to pull its view into a
// terminal window.
package main

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
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

	cfg := core.LoadConfig()
	projects, _ := core.ListProjects()
	if len(projects) == 0 {
		systray.AddMenuItem("no projects", "").Disable()
	}
	for _, p := range projects {
		title := p.Name
		if p.Pinned {
			title = "★ " + title
		}
		header := systray.AddMenuItem(title, "")
		header.Disable()
		for _, w := range p.Windows {
			st := watch(cfg, p, w)
			label := fmt.Sprintf("   %s%s — %s", w.RoleName(), stateMark(st), core.WindowStatus(w))
			item := systray.AddMenuItem(label, "click to open here")
			go onClick(item, done, p.Name, w.RoleName())
		}
		systray.AddSeparator()
	}

	refresh := systray.AddMenuItem("Refresh", "")
	go func(d chan struct{}) {
		for {
			select {
			case <-refresh.ClickedCh:
				requestRebuild()
			case <-d:
				return
			}
		}
	}(done)
	quit := systray.AddMenuItem("Quit", "")
	go func(d chan struct{}) {
		select {
		case <-quit.ClickedCh:
			systray.Quit()
		case <-d:
		}
	}(done)
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

func onClick(item *systray.MenuItem, d chan struct{}, project, role string) {
	for {
		select {
		case <-item.ClickedCh:
			openInTerminal(project, role)
		case <-d:
			return
		}
	}
}

// openInTerminal opens a Terminal.app window running "fm <project> go <role>";
// with go's move semantics the participant's view lands there and any other
// viewer detaches. The command reaches AppleScript as an argv item (never
// interpolated into the script) and every shell argument is quoted, so
// spaces or metacharacters in names and paths stay inert (issue #30).
func openInTerminal(project, role string) {
	fm := "fm"
	if exe, err := os.Executable(); err == nil {
		if sibling := filepath.Join(filepath.Dir(exe), "fm"); exists(sibling) {
			fm = sibling
		}
	}
	cmd := fmt.Sprintf("%s %s go %s", core.ShellQuote(fm), core.ShellQuote(project), core.ShellQuote(role))
	script := `on run argv
	tell application "Terminal"
		activate
		do script (item 1 of argv)
	end tell
end run`
	exec.Command("osascript", "-e", script, cmd).Run()
}

func exists(p string) bool {
	_, err := os.Stat(p)
	return err == nil
}
