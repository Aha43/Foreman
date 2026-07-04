# Changelog

All notable changes to foreman are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Pre-1.0 (`0.MINOR.PATCH`):
minor = features (breaking changes allowed), patch = fixes.

## [Unreleased]

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

[Unreleased]: https://github.com/Aha43/Foreman/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/Aha43/Foreman/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/Aha43/Foreman/releases/tag/v0.1.0
