package main

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
)

// The explorer can run as a macOS LaunchAgent so it starts at login and,
// crucially, survives its launch terminal — a plain `fm-explorer &` is a
// child of that terminal and dies when it closes (issue #80). Opt-in via
// `fm-explorer install-agent`; the menu's Quit still quits (see agentPlist).

const agentLabel = "com.github.aha43.foreman.explorer"

// stateDir holds the explorer's log — XDG-ish, matching foreman's own
// ~/.config and ~/.local/bin choices rather than ~/Library.
func stateDir() string {
	home, _ := os.UserHomeDir()
	return filepath.Join(home, ".local", "state", "foreman")
}

func logPath() string { return filepath.Join(stateDir(), "explorer.log") }

func agentPlistPath() string {
	home, _ := os.UserHomeDir()
	return filepath.Join(home, "Library", "LaunchAgents", agentLabel+".plist")
}

// agentPlist renders the LaunchAgent definition. KeepAlive fires only on a
// crash (SuccessfulExit=false) so a clean Quit from the menu stays quit while
// a vanish restarts; RunAtLoad starts it at login. launchd-captured crash
// output lands in the same log the in-process handler writes, so evidence
// survives however the process dies.
func agentPlist(exePath, log string) string {
	return fmt.Sprintf(`<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>Label</key>
	<string>%s</string>
	<key>ProgramArguments</key>
	<array>
		<string>%s</string>
	</array>
	<key>RunAtLoad</key>
	<true/>
	<key>KeepAlive</key>
	<dict>
		<key>SuccessfulExit</key>
		<false/>
	</dict>
	<key>StandardOutPath</key>
	<string>%s</string>
	<key>StandardErrorPath</key>
	<string>%s</string>
</dict>
</plist>
`, agentLabel, xmlEscape(exePath), xmlEscape(log), xmlEscape(log))
}

func xmlEscape(s string) string {
	s = strings.ReplaceAll(s, "&", "&amp;")
	s = strings.ReplaceAll(s, "<", "&lt;")
	s = strings.ReplaceAll(s, ">", "&gt;")
	return s
}

// installAgent writes the plist for this very binary and (re)loads it, then
// stops any explorer that was already running so the launchd-managed one is
// the only instance left — otherwise a manually-launched menu keeps running
// beside it and the user sees two icons (issue #80 review).
func installAgent() error {
	exePath, err := os.Executable()
	if err != nil {
		return err
	}
	if resolved, err := filepath.EvalSymlinks(exePath); err == nil {
		exePath = resolved
	}
	if err := os.MkdirAll(stateDir(), 0o755); err != nil {
		return err
	}
	plistPath := agentPlistPath()
	if err := os.MkdirAll(filepath.Dir(plistPath), 0o755); err != nil {
		return err
	}
	if err := os.WriteFile(plistPath, []byte(agentPlist(exePath, logPath())), 0o644); err != nil {
		return err
	}
	// Unload first so a reinstall over a loaded agent takes; nothing loaded
	// yet is not an error. Stop any manually-launched instance before load so
	// launchd's fresh one doesn't become a second icon.
	exec.Command("launchctl", "unload", plistPath).Run()
	terminateOtherInstances(exePath)
	if out, err := exec.Command("launchctl", "load", plistPath).CombinedOutput(); err != nil {
		return fmt.Errorf("launchctl load %s: %v: %s", plistPath, err, strings.TrimSpace(string(out)))
	}
	return nil
}

// terminateOtherInstances stops every running explorer except this
// short-lived install-agent process. Best-effort: no pgrep, no matches, or a
// kill that races an already-exiting process are all fine.
func terminateOtherInstances(exePath string) {
	self := os.Getpid()
	out, err := exec.Command("pgrep", "-x", filepath.Base(exePath)).Output()
	if err != nil {
		return
	}
	for _, field := range strings.Fields(string(out)) {
		pid, err := strconv.Atoi(field)
		if err != nil || pid == self {
			continue
		}
		exec.Command("kill", field).Run()
	}
}

// uninstallAgent unloads and removes the plist; a missing plist is fine.
func uninstallAgent() error {
	plistPath := agentPlistPath()
	exec.Command("launchctl", "unload", plistPath).Run()
	if err := os.Remove(plistPath); err != nil && !os.IsNotExist(err) {
		return err
	}
	return nil
}
