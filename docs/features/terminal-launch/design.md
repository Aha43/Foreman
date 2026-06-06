# Design: Terminal Launch and Focus

## Motivation

The session panel tells you what is active but doesn't help you find the actual terminal
window. With six or more sessions across two projects, you're still hunting through
unlabelled terminal tabs.

The fix is inverting the flow: Foreman opens the terminal, so Foreman controls the title,
the working directory, and the initial state. It can then find and focus that window on
demand — no hunting required.

## Behavior

### Launch (no terminal open yet)

Clicking "Launch" on a session row:
1. Opens a new Terminal.app window with working directory set to the project path
2. Sets the window title to the session label: `Foreman · [Project] / [Role]`
3. Copies the briefing text to the clipboard automatically (no extra click needed)
4. Marks the session as Running in the SessionRegistry
5. The user pastes the briefing into Claude Code immediately

### Focus (terminal already open)

Clicking "Focus" on a running session row:
1. Finds the Terminal.app window whose title matches the session label
2. Brings it to the front and activates Terminal.app
3. No new window, no lost context

### Session state

A session is **Running** if a Terminal.app window with its label exists.
A session is **Stopped** if no such window exists (or was never launched).

On Foreman startup: check all known sessions against open Terminal.app windows.
Sessions whose windows survived (e.g. Foreman restarted but terminals stayed open)
are restored to Running automatically.

This replaces the manual toggle entirely — state is derived from reality, not a flag.

## Window title convention

```
Foreman · [Project name] / [Role label]
```

Examples:
```
Foreman · Foreman / Dev Chat
Foreman · NamDesktop / Planning Chat
```

The `Foreman ·` prefix makes Foreman-managed windows easy to spot in the window
switcher (Cmd+`) and easy to search for programmatically.

## Implementation: macOS only

All terminal interaction goes through `osascript` (AppleScript via the macOS
`Runtime.exec`). Guarded by a `TerminalLauncher` interface with a
`MacOsTerminalLauncher` implementation — Windows/Linux stubs can be added later.

**Platform detection:** `System.getProperty("os.name").toLowerCase().contains("mac")`

**Launch script:**
```applescript
tell application "Terminal"
  set w to do script "cd '[path]' && clear"
  set custom title of w to "[label]"
  activate
end tell
```

**Focus script:**
```applescript
tell application "Terminal"
  repeat with w in windows
    if name of w contains "[label]" then
      set index of w to 1
      activate
      return
    end if
  end repeat
end tell
```

**Exists check (for startup restore):**
```applescript
tell application "Terminal"
  repeat with w in windows
    if name of w contains "[label]" then
      return "true"
    end if
  end repeat
  return "false"
end tell
```

Note: `custom title` sets the tab title. The `name` property of a window reflects
the tab title. The `contains` check rather than equality handles cases where
Terminal.app appends shell info to the title.

## Session panel changes

| State    | Button shown | Action                              |
|----------|-------------|--------------------------------------|
| Stopped  | Launch      | Open terminal, copy briefing, mark Running |
| Running  | Focus       | Bring terminal window to front       |

Status indicator: `● Running` (accent colour) / `○ Stopped` (muted).

The manual "Set Active / Set Idle" toggle is removed. State comes from the terminal.

## Briefing on launch

The briefing is generated (same as the Brief button) and placed on the system clipboard
before the terminal opens. The user pastes immediately after Claude Code starts. No
extra dialog — the Brief button still exists for standalone use.

## Out of scope

- iTerm2 support (add as follow-up, possibly as a settings option "preferred terminal")
- Windows / Linux support (stub the interface now, implement later)
- Automatic detection of terminals Foreman did not open
- Sending keystrokes to the terminal (paste automation)

## Issues

19. `TerminalLauncherService` — macOS osascript implementation behind a platform interface
    (`enhancement` + `session-tracking`)
20. Session panel: Launch/Focus buttons, state from terminal existence
    (`enhancement` + `ui`)

Issue 20 depends on 19.
