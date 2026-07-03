# foreman

Organize terminals by **project**, on top of tmux.

Modern dev work is terminal-heavy, and AI agents made it worse: several projects
run at once (you work on B while A's agents execute), each with a mix of agent
terminals and manual shells. Terminals get hunted for, typed into by mistake,
and closed by accident. foreman gives them an organizing principle — every
terminal is a named *participant* in a *project*.

```
foreman init                     write a starter config to edit
foreman list                     all projects and their terminals
foreman <project> new <role>     new terminal in the project (runs role preset, if any)
foreman <project> go <role>      jump to that terminal (from anywhere)
foreman <project> list           terminals in the project
foreman <project> adopt <role>   pull the terminal you're in into the project
foreman <project> done [role]    close one terminal, or the whole project
foreman <project> pin|unpin      mark the project as priority (sorts first in list)
```

Example session:

```
$ foreman A new planner          # starts claude in planner mode (role preset)
$ foreman A new shell            # plain shell for manual work
$ foreman A go planner           # jump there — attaches if outside tmux, switches if inside
$ foreman list
* A  2 terminals (attached)  planner:claude  shell:shell
  B  1 terminal              reviewer:claude
```

## How it maps to tmux

- **Project = tmux session** (named `fm-<project>` internally; you always type the bare name).
- **Terminal = tmux window**, tagged with a `@fm_role` user option. Lookups go by
  role metadata and immutable window id, never by display name — so `go planner`
  still works if something renames the window.
- **Identity is loud**: every managed session gets a colored status-bar badge with
  the project name (color is stable per project), so you always know what you're
  typing into.
- **Accidental close is harmless**: closing a terminal window only detaches a
  client; the session lives on in the tmux server. The only real teardown is the
  explicit `done` verb (whole-project `done` asks for confirmation).
- All state lives in the tmux server (user options `@fm_project`, `@fm_role`,
  `@fm_priority`) — no state files, nothing to drift out of sync.

## Install

Requires `tmux` (`brew install tmux`).

```
go build -o foreman .
mv foreman ~/.local/bin/        # or anywhere on PATH
```

## Config

`foreman init` writes a starter `~/.config/foreman/config.toml` (optional;
override path with `$FOREMAN_CONFIG`):

```toml
[role.planner]
cmd = "claude --permission-mode plan"

[role.reviewer]
cmd = "claude"

[project.A]
root = "~/code/project-a"
```

`new` starts terminals in the role's `dir`, else the project `root`, else your
current directory. Role commands run *inside* the shell, so the window survives
the process exiting. Unknown roles just get a plain shell — manual-work
terminals are first-class participants.

## Roadmap

- **Observation layer**: notice participant state changes without attaching —
  process finished (reliable, via tmux hooks like `pane-died` / `monitor-silence`)
  and agent-waiting-for-input (heuristic on `capture-pane` output). Surface in
  `foreman list`, as a status-bar ticker in other projects' terminals, and as
  macOS notifications — interrupting only for pinned (priority) projects.
- Cross-project status in the status bar (`A: planner waiting ⚠` while you're in B).
- `go --window`: open the terminal in a new macOS window instead of switching in place.
- tmux control mode (`tmux -CC`) event stream instead of polling, once the
  heuristics settle.
