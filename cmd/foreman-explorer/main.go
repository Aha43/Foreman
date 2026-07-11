// foreman-explorer is a macOS menu bar app over foreman's core: glance at
// projects and their participants, click one to pull its view into a
// terminal window.
package main

import (
	"context"
	"fmt"
	"log"
	"os"
	"os/exec"
	"path/filepath"
	"runtime/debug"
	"sort"
	"strings"
	"time"

	"fyne.io/systray"

	"github.com/arnehalvorsen/foreman/internal/core"
)

func main() {
	if len(os.Args) > 1 {
		runCommand(os.Args[1])
		return
	}
	setupCrashLog()
	systray.Run(onReady, nil)
}

// runCommand handles the non-menu invocations — the launchd agent lifecycle
// (issue #80). Bare `fm-explorer` still just runs the menu.
func runCommand(cmd string) {
	switch cmd {
	case "install-agent":
		fail(installAgent())
		fmt.Println("foreman explorer installed as a login agent — running now, and on every login.")
		fmt.Printf("logs: %s\n", logPath())
	case "uninstall-agent":
		fail(uninstallAgent())
		fmt.Println("foreman explorer login agent removed.")
	default:
		fmt.Fprintf(os.Stderr, "unknown command %q — use install-agent or uninstall-agent\n", cmd)
		os.Exit(1)
	}
}

func fail(err error) {
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

// setupCrashLog points the standard logger at the explorer's log file, so a
// panic (below) leaves evidence instead of a silent vanish (issue #80). A
// log we can't open is not fatal — the menu still matters more.
func setupCrashLog() {
	os.MkdirAll(stateDir(), 0o755)
	f, err := os.OpenFile(logPath(), os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o644)
	if err != nil {
		return
	}
	log.SetOutput(f)
}

func onReady() {
	systray.SetTitle("fm")
	systray.SetTooltip("foreman — terminals by project")
	// All menu work happens on this one goroutine — the timer and manual
	// Refresh both feed it, so the done channel and the systray menu are
	// never touched concurrently (issue #31). A panic here would silently
	// drop the menu bar icon; log it and exit non-zero so the evidence is
	// on disk and a launchd agent (issue #80) restarts us.
	go func() {
		defer func() {
			if r := recover(); r != nil {
				log.Printf("explorer refresher panic: %v\n%s", r, debug.Stack())
				os.Exit(1)
			}
		}()
		refresh()
		tick := time.Tick(10 * time.Second)
		for {
			select {
			case <-tick:
			case <-rebuildReq:
			}
			refresh()
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

// done is closed on every menu rebuild so click-listener goroutines from the
// previous menu generation exit instead of leaking. lastShape fingerprints
// the structure last built; participantItems are the live handles for
// in-place label updates between rebuilds (keyed by tmux window id). Only
// the refresher goroutine touches any of them.
var done chan struct{}
var lastShape = "\x00never built"
var participantItems map[string]*systray.MenuItem

// refresh is one tick: sweep, observe every participant (the notification
// pass), then render. The menu is torn down and rebuilt only when its shape
// — the set of projects, participants, pins, dormants — changed; label-only
// changes (an agent flipping between working and waiting, status text)
// update the existing items in place, so an open menu is never yanked shut
// under the user's cursor (issue #79). Action clicks change the shape, and
// the click already closed the menu — their refresh still rebuilds.
func refresh() {
	// Collect windows whose client ended without passing through go/done.
	core.SweepTermWindows()

	cfg := core.LoadConfig()
	projects, _ := core.ListProjects()
	labels := observe(cfg, projects)
	if shape := menuShape(cfg, projects); shape != lastShape {
		lastShape = shape
		rebuildMenu(cfg, projects, labels)
		return
	}
	for id, item := range participantItems {
		if label, ok := labels[id]; ok {
			item.SetTitle(label)
		}
	}
}

// observe runs the watch pass over every participant — state memory and
// waiting-notifications happen every tick no matter how the menu renders —
// and returns each window's fresh display label, keyed by window id.
func observe(cfg core.Config, projects []core.Project) map[string]string {
	labels := map[string]string{}
	for _, p := range projects {
		for _, w := range p.Windows {
			st := watch(cfg, p, w)
			labels[w.ID] = fmt.Sprintf("   %s%s — %s", w.RoleName(), stateMark(st), core.WindowStatus(w))
		}
	}
	return labels
}

// menuShape fingerprints everything whose change requires re-adding menu
// items — systray can retitle items in place, but not change membership:
// the projects (order and pin state), their participants (id and role),
// and the config-derived entries (role choices, dormant projects).
func menuShape(cfg core.Config, projects []core.Project) string {
	var b strings.Builder
	for _, p := range projects {
		fmt.Fprintf(&b, "%s|%v", p.Name, p.Pinned)
		for _, w := range p.Windows {
			fmt.Fprintf(&b, ";%s=%s", w.ID, w.RoleName())
		}
		b.WriteByte('\n')
	}
	b.WriteString(strings.Join(roleChoices(cfg), ","))
	b.WriteByte('\n')
	b.WriteString(strings.Join(dormantNames(cfg, projects), ","))
	return b.String()
}

func rebuildMenu(cfg core.Config, projects []core.Project, labels map[string]string) {
	if done != nil {
		close(done)
	}
	done = make(chan struct{})
	systray.ResetMenu()
	participantItems = map[string]*systray.MenuItem{}

	if len(projects) == 0 {
		systray.AddMenuItem("no projects", "").Disable()
	}
	for _, p := range projects {
		buildProjectMenu(cfg, p, labels, done)
		systray.AddSeparator()
	}
	buildDormant(cfg, projects, done)

	listen(systray.AddMenuItem("Refresh", ""), done, requestRebuild)
	listen(systray.AddMenuItem("Quit", ""), done, systray.Quit)
}

// buildProjectMenu adds one project's block to the menu: a header opening a
// submenu of actions on the project, then one top-level item per participant
// — participants stay at the top level so the at-a-glance view and the
// one-click "pull it here" survive the actions moving in (issue #68).
func buildProjectMenu(cfg core.Config, p core.Project, labels map[string]string, d chan struct{}) {
	title := p.Name
	if p.Pinned {
		title = "★ " + title
	}
	// The header must stay enabled for its submenu to open; it has no click
	// listener, so selecting the header itself still does nothing.
	header := systray.AddMenuItem(title, "project actions")
	buildNewParticipant(cfg, p, header, d)
	// Pin/unpin is one toggle, labeled by what a click would do — judged
	// from the project's current @fm_priority (issue #71).
	pinLabel, pinVerb := "Pin", "pin"
	if p.Pinned {
		pinLabel, pinVerb = "Unpin", "unpin"
	}
	pin := header.AddSubMenuItem(pinLabel, "pinned projects sort first and post notifications")
	project, verb := p.Name, pinVerb
	listen(pin, d, func() {
		execFm(project, verb)
		requestRebuild()
	})
	// Done entries sit behind the header submenu and a confirmation dialog —
	// two steps from the top-level go-click, on purpose (issue #72). The
	// ellipsis is the macOS "asks before acting" convention.
	if len(p.Windows) > 0 {
		header.AddSeparator()
	}
	for _, w := range p.Windows {
		item := header.AddSubMenuItem("Done "+w.RoleName()+"…", "end this participant — closes its terminal for real")
		project, role := p.Name, w.RoleName()
		listen(item, d, func() {
			if confirmDone(project, role) {
				execFm(project, "done", role)
				requestRebuild()
			}
		})
	}
	for _, w := range p.Windows {
		item := systray.AddMenuItem(labels[w.ID], "click to open here")
		participantItems[w.ID] = item
		project, role := p.Name, w.RoleName()
		listen(item, d, func() { openInTerminal(project, role) })
	}
}

// buildNewParticipant fills the project's actions submenu with "New
// participant" role entries. Roles the project already has are shown
// disabled — new would only refuse them (issue #69).
func buildNewParticipant(cfg core.Config, p core.Project, header *systray.MenuItem, d chan struct{}) {
	menu := header.AddSubMenuItem("New participant", "start a terminal in this project")
	have := map[string]bool{}
	for _, w := range p.Windows {
		have[w.RoleName()] = true
	}
	for _, r := range roleChoices(cfg) {
		item := menu.AddSubMenuItem(r, "")
		if have[r] {
			item.Disable()
			continue
		}
		project, role := p.Name, r
		listen(item, d, func() { newParticipant(project, role) })
	}
}

// roleChoices is what a new participant may start as: every configured role
// plus shell — unknown roles get a plain shell, so it is always valid.
func roleChoices(cfg core.Config) []string {
	roles := make([]string, 0, len(cfg.Roles)+1)
	for r := range cfg.Roles {
		roles = append(roles, r)
	}
	if _, ok := cfg.Roles["shell"]; !ok {
		roles = append(roles, "shell")
	}
	sort.Strings(roles)
	return roles
}

// buildDormant lists configured projects with no live session — "create new
// project" without inventing anything: a project exists when its session
// does, and starting one is just new on a project not running yet
// (issue #70). Each dormant project opens the same role choices as "New
// participant"; the first participant brings the project to life.
func buildDormant(cfg core.Config, projects []core.Project, d chan struct{}) {
	dormant := dormantNames(cfg, projects)
	if len(dormant) == 0 {
		return
	}
	systray.AddMenuItem("start a project", "configured, not running").Disable()
	for _, name := range dormant {
		menu := systray.AddMenuItem("   "+name, "start this project")
		for _, r := range roleChoices(cfg) {
			item := menu.AddSubMenuItem(r, "")
			project, role := name, r
			listen(item, d, func() { newParticipant(project, role) })
		}
	}
	systray.AddSeparator()
}

// dormantNames is the configured projects with no live session, sorted.
func dormantNames(cfg core.Config, projects []core.Project) []string {
	live := map[string]bool{}
	for _, p := range projects {
		live[p.Name] = true
	}
	var dormant []string
	for name := range cfg.Projects {
		if !live[name] {
			dormant = append(dormant, name)
		}
	}
	sort.Strings(dormant)
	return dormant
}

// newParticipant creates the terminal (fm new without a TTY creates without
// going), then pulls it into a Terminal window — you create a terminal to
// use it. The menu refreshes either way so it reflects whatever actually
// happened.
func newParticipant(project, role string) {
	if execFm(project, "new", role) {
		openInTerminal(project, role)
	}
	requestRebuild()
}

// execFm runs an fm verb silently and reports whether it succeeded; a
// failure surfaces the CLI's own message as a notification.
func execFm(args ...string) bool {
	out, err := exec.Command(fmBinary(), args...).CombinedOutput()
	if err == nil {
		return true
	}
	msg := strings.TrimSpace(string(out))
	if msg == "" {
		msg = err.Error()
	}
	notify(msg)
	return false
}

// listen fires fn on every click of item until this menu generation is torn
// down (d closes), so listener goroutines never outlive their menu.
func listen(item *systray.MenuItem, d chan struct{}, fn func()) {
	go func() {
		for {
			select {
			case <-item.ClickedCh:
				safely(fn)
			case <-d:
				return
			}
		}
	}()
}

// safely runs a click handler so a panic in one action is logged and
// contained — it can't take the whole menu bar app down with it (issue #80).
func safely(fn func()) {
	defer func() {
		if r := recover(); r != nil {
			log.Printf("explorer action panic: %v\n%s", r, debug.Stack())
		}
	}()
	fn()
}

// watch is the observation epic's memory half (issue #5): compare this
// look with the last one (stored as a tmux window option), record the new
// state, and notify on a transition into waiting — pinned projects only,
// everything else stays glanceable in this menu. Runs on the single
// refresher goroutine, every 10s poll.
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

// confirmDone asks before teardown — "closing is harmless, ending is
// explicit": a menu misclick must never kill a participant (issue #72).
// The message travels as an osascript argument (#30 rule). Bounded like all
// automation (#62) but on a human scale: the dialog gives itself up after
// 60s (with a Go-side backstop), and anything but an explicit Done — cancel,
// give-up, timeout, error — answers no.
func confirmDone(project, role string) bool {
	ctx, cancel := context.WithTimeout(context.Background(), 75*time.Second)
	defer cancel()
	err := exec.CommandContext(ctx, "osascript",
		"-e", "on run argv",
		"-e", `set r to display dialog (item 1 of argv) with title "foreman" buttons {"Cancel", "Done"} default button "Cancel" cancel button "Cancel" with icon caution giving up after 60`,
		"-e", `if gave up of r then error number -128`,
		"-e", "end run",
		fmt.Sprintf("End %s in project %s? Its terminal closes for real.", role, project)).Run()
	return err == nil
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
