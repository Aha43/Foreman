# Foreman

AI team session manager for Claude Code.

When you run a Planner, a Dev, and a Dogfooder in parallel — each in its own terminal — Foreman keeps track of what is open, launches new sessions with a structured briefing, and brings any session to front on demand. It solves the "which chat is which" problem.

> macOS only for terminal launch. Non-macOS builds run with a manual active/idle toggle instead.

---

## How it works

**Projects** — register a local directory. Foreman reads your `docs/roles/*.md` files and builds a role library automatically.

**Sessions** — each project × role combination is a session. Foreman tracks which terminals are open.

**Launch** — opens a titled Terminal.app window, starts Claude Code, and sends a structured briefing as its opening message. No paste needed.

**Brief** — generates a handover briefing: your role instructions, design docs under `docs/features/`, and open GitHub issues fetched via `gh`. Available as a standalone copy action too.

**Focus** — click Focus on any running session to bring that terminal window to front. No hunting through tabs.

---

## Project structure convention

Foreman looks for role definitions and design docs in a conventional location:

```
your-project/
  docs/
    roles/
      planning.md       ← first heading becomes the role name
      dev.md
      dogfooding.md
    features/
      <feature>/
        design.md       ← included in briefings automatically
  CLAUDE.md             ← always listed first in the briefing
```

**Tolerant, not strict.** If `docs/roles/` is absent the project registers with an empty team. If `CLAUDE.md` is absent it is omitted from the briefing. No errors, no prompts.

### Role file format

The role name is read from the first line of the file:

```markdown
# Role: Dev Chat       ← preferred form
# Dev Chat             ← also accepted
```

If neither heading form is present, the filename is used (`dev-chat.md` → `Dev Chat`).

---

## Sidecar projects

For external repos or client codebases you cannot modify, you can keep your workflow artifacts in a separate sidecar directory:

```
~/foreman-sidecars/their-repo/
  docs/
    roles/
      dev.md
    features/
      ...
```

When registering a project, set an optional **Workflow path** pointing at the sidecar. Foreman discovers roles and design docs from there instead. The terminal still launches in the actual project directory. The generated briefing distinguishes the two paths so the AI knows where to look.

---

## Getting started

### Requirements

- Java 17+
- macOS (Terminal.app integration; other platforms get a manual toggle)
- [`gh`](https://cli.github.com) CLI — optional, used to fetch open issues for briefings
- PowerShell (`pwsh`) — to run the dependency download script

### 1. Download dependencies

```powershell
pwsh scripts/download-libs.ps1
```

This fetches FlatLaf, Jackson, and JSVG from Maven Central into `lib/`.

### 2. Build

```bash
make
```

Output lands in `build/app/`.

### 3. Run

```bash
make run
```

On first launch, the workspace is empty. Click **Register Project** (or ⌘R) and point it at a project directory. Foreman will discover its roles and add it to the list.

---

## Build commands

| Command       | What it does                                      |
|---------------|---------------------------------------------------|
| `make`        | Compile, package JAR, copy deps → `build/app/`   |
| `make run`    | Build then launch (production workspace)          |
| `make run-dev`| Build then launch (separate dev workspace)        |
| `make test`   | Compile and run unit tests                        |
| `make clean`  | Delete `build/`                                   |

Workspace files live in `~/.foreman/`. The dev mode uses `~/.foreman/dev/` so you can experiment without touching your real data.

---

## Architecture

Foreman is a Java Swing desktop application with no build tool (no Maven, no Gradle).

| Layer        | What it contains |
|--------------|-----------------|
| `foreman.domain` | `Project`, `Role`, `RoleAssignment`, `Team`, `Session`, `ForemanWorkspace` — plain records |
| `foreman.persistence` | `ForemanWorkspaceRepository` — load/save to `~/.foreman/workspace.json` via Jackson |
| `foreman.app` | Services: workspace, role discovery, project registration, briefing, terminal launch, settings |
| `foreman.ui` | Swing panels and dialogs |

**Dependencies** (all in `lib/`):

- [FlatLaf](https://www.formdev.com/flatlaf/) — cross-platform Swing look-and-feel
- [Jackson](https://github.com/FasterXML/jackson) — JSON persistence
- [JSVG](https://github.com/weisJ/jsvg) — SVG icon rendering
