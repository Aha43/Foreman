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
	rebuild()
	go func() {
		for range time.Tick(10 * time.Second) {
			rebuild()
		}
	}()
}

// done is closed on every rebuild so click-listener goroutines from the
// previous menu generation exit instead of leaking.
var done chan struct{}

func rebuild() {
	if done != nil {
		close(done)
	}
	done = make(chan struct{})
	systray.ResetMenu()

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
			label := fmt.Sprintf("   %s — %s", w.RoleName(), core.WindowStatus(w))
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
				rebuild()
				return
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
// viewer detaches.
func openInTerminal(project, role string) {
	fm := "fm"
	if exe, err := os.Executable(); err == nil {
		if sibling := filepath.Join(filepath.Dir(exe), "fm"); exists(sibling) {
			fm = sibling
		}
	}
	script := fmt.Sprintf(`tell application "Terminal"
	activate
	do script "%s %s go %s"
end tell`, fm, project, role)
	exec.Command("osascript", "-e", script).Run()
}

func exists(p string) bool {
	_, err := os.Stat(p)
	return err == nil
}
