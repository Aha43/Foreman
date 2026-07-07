# Changelog

All notable changes to foreman are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Pre-1.0 (`0.MINOR.PATCH`):
minor = features (breaking changes allowed), patch = fixes.

## [Unreleased]

### Added

- **New participants from the explorer.** Each project's submenu gains "New participant"
  listing the configured roles plus `shell`; clicking one creates the terminal and pulls it
  into a Terminal window, same as clicking a participant. Roles the project already has are
  shown disabled. Failures surface as a notification. Closes #69.

- **Pin and unpin from the explorer.** Each project's submenu gains a Pin/Unpin toggle —
  one item, labeled by what a click would do — over the CLI's `pin`/`unpin` verbs. The ★
  and sort order update immediately instead of waiting out the 10-second poll. Closes #71.

### Changed

- **Explorer project headers are now submenus.** Each project's header opens a submenu that
  will host the Sprint 6 actions (new participant, pin/unpin, done); until those land it
  holds a placeholder. Participants stay top-level items — the at-a-glance view and the
  one-click "pull it here" are unchanged. Closes #68.

## [0.5.1] - 2026-07-05

The cross-machine hardening release: an Intel-Mac install surfaced two real-world bugs
(pty-recycled window tracking, the missing `coder` template role) fixed there and
counter-verified here, then a Codex review of that work found four more lifecycle holes —
all closed before this cut. Window cleanup is now id-keyed, race-checked, protocol-aware,
and time-bounded.

### Fixed

- **A hung Terminal can't freeze foreman.** All Terminal automation now runs under a 15-second
  timeout — a stuck AppleEvent no longer blocks `go`, `done`'s cleanup worker, or the explorer's
  poll forever; timeouts count as errors, so tracking survives for the sweep to retry. Found by
  Codex review. Closes #62.

- **Opening a window that can't be tracked is now an error.** A Terminal reply that didn't
  parse silently skipped tracking, and a failed tmux write was ignored — `go --window` and
  explorer clicks reported success for windows cleanup could never find. The reply is now
  validated strictly and tracking persistence failures surface to the caller. Found by Codex
  review. Closes #61.

- **The window bookkeeping can't resurrect retired entries.** The CLI's record step and the
  explorer's sweep race over the same tmux options from different processes; a sweep acting on
  a stale snapshot could re-create an entry the record had just retired — one whose recycled
  tty then reads as attached forever. Every mutation now verifies against a fresh read first.
  Found by Codex review. Closes #60.

- **A slow terminal teardown can't strand a husk anymore.** The window-close script reports
  what it did (`closed`/`gone`/`notab`/`kepttab`/`busy`), but the caller forgot the tracking
  entry on any clean exit — so a tab still busy after the grace period (possibly a tmux
  teardown just slower than 2s) became a permanently forgotten husk. `busy` now keeps the
  entry for the sweep to re-judge; only genuinely terminal outcomes forget it. Found by Codex
  review. Closes #59.

- **`new coder` no longer looks broken.** The docs use `coder` as the canonical role, but the
  shipped config template only defined `planner` and `agent` — so following the README opened a
  plain shell with no claude, silently. The template now ships `[role.coder]`, and `new` prints
  a notice when the role isn't configured at all instead of leaving the shell unexplained
  (a role deliberately configured without a `cmd` stays quiet).

- **Window cleanup actually closes windows.** The 0.5.0 lifecycle tracked windows by tty, but
  macOS recycles a pty the instant its shell exits and dead tabs keep reporting their stale
  tty — so tracking entries got overwritten (husks forgotten forever) and a recycled tty could
  match tabs in the wrong window. Worse, Terminal 2.15 rejects `close` on tab objects outright,
  an error the old code swallowed while forgetting the window anyway — on Macs with that
  Terminal, *no* window ever closed. Tracking is now keyed by Terminal's window id (unique,
  never reused) with the tty as the liveness value; single-tab windows are closed as windows;
  a close that fails is retried by the sweep instead of forgotten; a tab that stays busy is
  left to its user (never a confirmation sheet), and old-format tracking entries migrate on
  the first sweep. Closes #56.

## [0.5.0] - 2026-07-04

Terminal window lifecycle: foreman now closes the windows it opens once their view moves away —
no more `[detached]` husks — and `go --window` opens views in fresh tracked windows. Hardened by
a Codex review cycle (three lifecycle findings, all fixed pre-release: tab-precise closing,
uncertainty-safe two-strike sweeping, and cleanup that survives `done` killing its own process).

### Fixed

- **Window cleanup can't overreach.** The explorer's sweep aborts when tmux's client list can't
  be read (uncertainty no longer looks like "no clients" and reaps every tracked window), and a
  window is only closed after two consecutive absent observations — so a just-opened window
  whose client hasn't attached yet survives. Cleanup also closes only the foreman *tab*, never
  a whole window carrying user-added tabs. Found by Codex review. Closes #47, #48.

### Added

- **`go --window`.** Open a participant's view in a new Terminal window instead of switching
  the current one in place — the window is tracked, so it's cleaned up again when the view
  moves on. Closes #6.

- **No more soulless windows.** Terminal windows that foreman opens (explorer clicks) are now
  tracked — in tmux server options, like everything else — and **closed automatically** the
  moment their view moves away: `go` detaching viewers, whole-project `done`, or the explorer's
  sweep for stragglers. Windows you opened yourself are never touched. Closes #43.

## [0.4.0] - 2026-07-04

The observation epic completed: the status bar tickers who's waiting in other projects (tmux
itself does the scheduling — no daemon), the explorer notifies when a pinned project's agent
wants you (its memory stored in the tmux server, like everything else), and the how-it-works
doc explains the whole model for tmux newcomers. Hardened by a Codex review (two findings,
both fixed pre-release) — a rhythm now codified in `.codex-review`.

### Added

- **Cross-project ticker in the status bar.** Every managed terminal's status bar now shows,
  on the right, who's waiting for you in *other* projects (`B: planner⚠`) — empty when nobody
  is. tmux itself re-runs the check on its redraw schedule (~15s): no daemon, no stored state.
  Applies to sessions created/adopted from now on. Closes #4.

- **macOS notifications when an agent starts waiting.** The explorer's 10-second poll now
  remembers each participant's last state (as a tmux window option — the memory lives in the
  server too) and posts a notification on the transition into `waiting` — for **pinned**
  projects only; everything else stays glanceable in the menu, which now also carries the
  ⚠/○ state marks. Closes #5.

- **"How foreman works" doc.** `docs/how-it-works.md` — the terminal-server model, foreman's
  thin semantic layer, where state lives, and snapshot-vs-memory, written for readers new to
  tmux. Linked from the README, whose roadmap now reflects the completed observation epic.
  Closes #24.

### Fixed

- **One classifier for all observation surfaces.** The ticker used a cheaper title-only check,
  so a titleless agent at a y/n prompt could show `waiting⚠` in `foreman list` and trigger a
  notification while missing from the ticker. All three surfaces now share the full classifier —
  the capture fallback only ever runs for titleless agent windows, which are rare. Found by
  Codex review. Closes #38.

- **The ticker survives the binary moving.** The status-bar command now prefers the
  PATH-resolved `foreman` over the running executable's path (which can be a `go run` temp dir
  or scratch build), and session styling is re-stamped on `go` and `pin` — so existing sessions
  self-heal toward the binary you actually use. Found by Codex review. Closes #37.

- **Explorer: clicking a participant is injection-safe.** The `fm … go …` command reaches
  AppleScript as an argument (never spliced into the script) and every shell argument is
  quoted — so spaces, quotes, or metacharacters in project/role names or the fm path open the
  right terminal instead of breaking or executing something else. Found by Codex review.
  Closes #30.

- **Explorer: Refresh can't race the timer anymore.** The 10-second timer and the Refresh menu
  item both triggered menu rebuilds concurrently — two at once could close the same channel
  twice (a crash) or mutate the menu mid-rebuild. All rebuilds now run on one goroutine; Refresh
  just asks it to go. Found by Codex review. Closes #31.

## [0.3.0] - 2026-07-04

The observation layer's core: foreman now tells you what every agent is doing — working,
waiting for you, done, or gone — without attaching. Built on the testing foundation laid the
same day (25+ Go tests behind a tmux seam, CI on every PR), and shaken down live against real
Claude Code sessions, including a genuine permission prompt.

### Fixed

- **Scripted `new` no longer prompts (or worse, auto-goes).** `/dev/null` is a character
  device, so the old TTY check misfired on `fm <p> new <role> < /dev/null` — showing the
  `go to X? [Y/n]` prompt, reading EOF as default-yes, and failing with `open terminal failed`.
  Now uses a real terminal check, and an EOF/read error on the prompt never means yes. Found by
  the observation-layer live shakedown. Closes #28.

### Added

- **Observation: reliable participant states.** New `State` snapshot in core — `exited` (pane
  dead), `done` (the role's command finished and dropped back to the shell), `shell` (manual
  terminal), else `working`. Computed fresh from the tmux server on demand, nothing stored.
  Closes #1.

- **Observation: the `waiting` state.** An agent that wants your attention is detected
  title-first — Claude Code sets a braille spinner while busy and `✳` when idle — with a
  small, table-tested pane-content fallback (selector cursor, `[y/n]` prompts in the last
  visible lines) for agents that don't announce themselves. Defaults to `working` when unsure.
  Closes #2.

- **Participant state in `foreman list`.** The project list gains a STATE column next to the
  pane title; the global list marks only the states that want a glance on the role tag —
  `planner⚠` (waiting), `build○` (done) — so `working` stays visually quiet. Closes #3.

- **CI.** A GitHub Actions `check` workflow runs `make check` (gofmt + vet + test) on every PR
  and push to `main` — the automated version of the workflow doc's PR gate. Closes #21.

- **Tests for `internal/core`.** Table-driven tests for project inference (`projectByRoot`,
  `CurrentProject` — all three sources), config parsing, `WorkDir` precedence, window/status
  parsing and `FindWindow`'s role-beats-name rule, `ListProjects` sorting/filtering, `HumanAge`,
  and `WindowStatus`. Tmux-facing code is tested through a new seam (`tmuxRun`) that feeds
  canned tmux output — the same seam the observation layer will build on. `make check` has
  teeth now. Closes #20.

## [0.2.0] - 2026-07-04

Sprint 1 — CLI ergonomics: the project name becomes optional, `new` flows straight into `go`,
plus the license, a makefile, and two release-blockers caught by a Codex review.

### Added

- **Makefile.** `make install` builds and installs `foreman` (+ `fm` symlink) and `fm-explorer`
  into `~/.local/bin` (override with `BIN=`); `make check` is the gofmt+vet+test gate from the
  workflow docs. Closes #14.

- **The project name can be omitted.** `foreman new coder` (and `go`, `adopt`, `done`,
  `pin`/`unpin`) infers the project: inside a managed terminal, that terminal's project;
  otherwise the configured `root` containing the current directory; otherwise the directory's
  name. The decision is echoed once (`foreman: project NamWeb (from config root)`). Verb names
  become reserved project names; bare `foreman list` stays the all-projects overview. Closes #8.

- **`new` offers to go to the just-created terminal.** After creating, `new` asks
  `go to coder? [Y/n]` — plain Enter jumps there via the normal `go` move semantics. The prompt
  only appears on a TTY, so scripted use creates without going, as before. Closes #9.

- **MIT license.** The repo is now properly open source. Closes #10.

### Fixed

- **`adopt` rejects duplicate roles.** Adopting a terminal under a role the project already has
  would create two windows with the same identity, making `go`/`done` resolve whichever came
  first — possibly switching to or killing the wrong terminal. `adopt` now errors like `new`
  does. Found by Codex review. Closes #17.

- **The explorer's source is actually in the repository.** The unanchored `.gitignore` patterns
  (`foreman-explorer`) also matched `cmd/foreman-explorer/`, so its source was never committed —
  a fresh clone couldn't build. Ignores are now anchored to the repo root and the missing source
  is committed. Found by Codex review. Closes #16.

## [0.1.0] - 2026-07-04

The MVP: project-organized terminals on top of tmux, plus the macOS menu bar explorer.

### Added

- **The core CLI.** Projects map to tmux sessions, terminals to windows tagged with role
  metadata: `foreman list`, `foreman <project> new|go|list|adopt|done|pin|unpin`. Identity is
  loud (per-project colored status-bar badge), lookups go by role + immutable window id, and all
  state lives in the tmux server — no state files.
- **`foreman init`** — scaffolds a starter `~/.config/foreman/config.toml` with role presets
  (e.g. `planner` runs `claude --permission-mode plan`) and project roots.
- **`go` moves the view instead of adding one** — navigating never multiplies terminals;
  `--mirror` keeps other views for the side-by-side case.
- **foreman-explorer** — a macOS menu bar companion: glance at every project and participant,
  click to pull its view into a Terminal window. Pinned projects show a ★.
- `foreman list` shows the attached client count per project.
- Status display prefers the pane title over the process name.

[Unreleased]: https://github.com/Aha43/Foreman/compare/v0.5.1...HEAD
[0.5.1]: https://github.com/Aha43/Foreman/compare/v0.5.0...v0.5.1
[0.5.0]: https://github.com/Aha43/Foreman/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/Aha43/Foreman/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/Aha43/Foreman/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/Aha43/Foreman/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/Aha43/Foreman/releases/tag/v0.1.0
