# foreman

Organize terminals by **project**, on top of tmux.
New to the terminal-server idea? Read [how foreman works](docs/how-it-works.md).

Modern dev work is terminal-heavy, and AI agents made it worse: several projects
run at once (you work on B while A's agents execute), each with a mix of agent
terminals and manual shells. Terminals get hunted for, typed into by mistake,
and closed by accident. foreman gives them an organizing principle — every
terminal is a named *participant* in a *project*.

```
foreman init                     write a starter config to edit
foreman list                     all projects and their terminals
foreman <project> new <role>     new terminal in the project (runs role preset, if any)
foreman <project> go <role>      move that terminal's view here (--mirror to view alongside)
foreman <project> list           terminals in the project
foreman <project> adopt <role>   pull the terminal you're in into the project
foreman <project> done [role]    close one terminal, or the whole project
foreman <project> pin|unpin      mark the project as priority (sorts first in list)
```

The project name can be omitted — `foreman new coder` infers it: inside a
managed terminal, that terminal's project; otherwise the configured project
`root` containing your current directory; otherwise the directory's name.
(Verb names — `new`, `go`, `list`, … — are therefore reserved as project
names. Bare `foreman list` always shows all projects.)

After creating a terminal, `new` offers to jump to it (`go to coder? [Y/n]`,
plain Enter goes). The prompt only appears on a TTY — scripted use creates
without going.

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
- **One place at a time**: `go` moves the project's view to the terminal you ran
  it in, detaching other viewers — navigating never multiplies terminals.
  `go <role> --mirror` keeps the other views for the rare side-by-side case.
- **Accidental close is harmless**: closing a terminal window only detaches a
  client; the session lives on in the tmux server. The only real teardown is the
  explicit `done` verb (whole-project `done` asks for confirmation).
- All state lives in the tmux server (user options `@fm_project`, `@fm_role`,
  `@fm_priority`) — no state files, nothing to drift out of sync.

## Install

Requires `tmux` (`brew install tmux`).

```
make install                    # foreman + fm symlink + fm-explorer into ~/.local/bin
```

(`make help` lists the other targets; `BIN=/somewhere make install` to install
elsewhere on PATH.)

## Explorer (menu bar app)

A macOS menu bar companion: glance at every project and participant, click one
to pull its view into a Terminal window (same move semantics as `go`).

```
fm-explorer &                   # installed by make install; shows "fm" in the menu bar
```

The menu refreshes every 10 seconds. Pinned projects show a ★.

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

## Observation

foreman tells you what every participant is doing without attaching: `foreman list` shows a
STATE per terminal (`working`, `waiting⚠`, `done○`, `shell`, `exited`), the status bar of
each managed terminal tickers who's waiting in *other* projects, and the explorer posts a
macOS notification when an agent in a **pinned** project starts waiting. Detection reads
what agents announce (Claude Code signals busy/idle in its pane title) with a careful
content fallback — see [how it works](docs/how-it-works.md).

## Roadmap

Tracked as [GitHub issues](https://github.com/Aha43/Foreman/issues), grouped into sprint
milestones — see [docs/workflow.md](docs/workflow.md). The broad strokes:

- `go --window`: open the terminal in a new macOS window instead of switching in place.
- tmux control mode (`tmux -CC`) event stream instead of polling, once the
  heuristics settle.

## License

[MIT](LICENSE)
