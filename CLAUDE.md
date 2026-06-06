# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Always present a plan and wait for explicit approval before editing any files or running commands.

## Build commands

```bash
make          # compile, package JAR, copy deps -> build/app/
make run      # build then launch the app
make clean    # delete build/
```

To download dependencies before the first build:

```powershell
pwsh scripts/download-libs.ps1
```

## Architecture

Foreman is a Java Swing desktop application built without a build tool (no Maven/Gradle).
Source lives under `src/foreman/`, dependencies under `lib/`, build output under `build/`.

### Entry point

`foreman.app.ForemanMain` — wires up Look & Feel, creates the main `JFrame`, and shows it on the EDT.

### Key packages

| Package | Responsibility |
|---|---|
| `foreman.app` | Entry point, `AppInfo` (name + version) |
| `foreman.ui` | Main frame and top-level Swing panels |

### Dependencies (`lib/`)

- **FlatLaf** — cross-platform Swing look-and-feel (light/dark themes)
- **Jackson** — JSON serialisation for workspace persistence
- **JSVG** — SVG icon support

Run `pwsh scripts/download-libs.ps1` to download all of the above from Maven Central.

## Workflow

- **Always work on a GitHub issue.** Never start implementation without a corresponding issue —
  either one created upfront or one we create together before coding begins.
  Include `Closes #<number>` in every commit that resolves an issue.
- **Always check the current branch before committing.** If on `main`, warn and stop.
  All feature work must go on a feature branch.
- **One branch per sprint, one PR per sprint.** The branch name matches the sprint milestone
  (e.g. `feature/sprint-6-ux-polish`). Do not open a PR until all issues in the sprint
  milestone are complete and the user has confirmed the sprint is done.
- When completing a GitHub issue, update the `## [Unreleased]` section of `CHANGELOG.md`
  before committing. Use `Added`, `Changed`, or `Fixed` as appropriate, and include
  `Closes #<number>` in the commit message.
- **Always run `make run` after every change** — even trivial ones — so the user can test
  before committing. Never skip this step.
- **Always run `make test` before committing** to confirm existing tests still pass.
- **One issue at a time.** After completing an issue, stop and wait for the user to confirm
  before starting the next one — even when multiple issues are planned for the same sprint.

### Definition of Done for feature issues

A feature issue is complete when:
- the feature works (`make run` verified)
- relevant unit tests are added or updated
- all existing tests pass (`make test`)
- no obvious domain invariant is weakened

### Testing preference

Prefer small, focused unit tests around domain model, persistence, and service layer before UI tests.
Swing UI tests can wait unless behaviour cannot be verified elsewhere.

## Conventions

- All Swing work must happen on the Event Dispatch Thread (`SwingUtilities.invokeLater`).
- Prefer `var` for local variables where the type is obvious from context.
- No Maven/Gradle — keep the build simple and direct.
