# Design: Project Discovery + Session Panel

## Motivation

The workspace is always empty on a fresh install. The admin path (forms for adding
projects and roles one field at a time) is the wrong first experience. Most projects
a Foreman user works on already follow or will follow a consistent structure. Foreman
should be able to read that structure and bootstrap itself from it.

This epic also introduces the session panel — the core proof-of-concept for the
"which chat is which" problem that motivated the app.

## Convention: Foreman Project Structure

Foreman is opinionated about where roles live. The happy path:

```
<project-root>/
  docs/
    roles/
      planning.md
      dev.md
      dogfooding.md
  CLAUDE.md
```

**Tolerant, not strict:** if `docs/roles/` is absent, the project registers with an
empty team. If `CLAUDE.md` is absent, description is left blank. No errors, no prompts
— just less automatic data.

## Role Discovery

Scan `docs/roles/*.md` (non-recursive). For each file:

1. **Role name** — read the first line of the file. If it matches `# Role: <name>` or
   `# <name>`, use `<name>`. Otherwise fall back to the filename: strip `.md`,
   replace `-` and `_` with spaces, title-case.
2. **Instructions** — full file content (as-is, the whole markdown).
3. **Deduplication** — before adding a discovered role to the library, check if a Role
   with the same name already exists. If it does, reuse it (do not duplicate). This
   means registering Foreman and NamDesktop both with a "Dev" role shares one library
   entry.

## Project Registration Flow

1. User clicks "Register Project" (button in the project list panel).
2. A dialog appears with a single path field + a Browse button (JFileChooser, directories only).
3. Foreman derives the project name from the directory name (user can override in the field).
4. On confirm:
   - Scan `docs/roles/*.md` under the given path.
   - For each discovered role: add to library if not already present (by name).
   - Create a `Team` with one `RoleAssignment` per discovered role.
   - Create the `Project` and add to workspace.
   - Save workspace.
5. The project list panel refreshes and selects the new project.

## Session Panel

A new top-level panel alongside the project list. It answers the question:
**"Which chats do I have open right now, and for what?"**

### Data model addition

`Session` — in-memory only (not persisted):

```
Session
  id:        String (UUID, assigned when activated)
  projectId: String
  roleId:    String
  active:    boolean
```

`SessionRegistry` — in-memory singleton holding the current set of sessions.
Populated manually by the user; cleared on app restart.

### UI layout

The main frame becomes a split between a left sidebar and a right content area.
The left sidebar has two sections stacked vertically:

```
[ Register Project ]          ← button

Projects
  Foreman          >
  NamDesktop       >

──────────────────────────────
Active sessions
  Foreman  /  Dev        ●
  NamDesktop  /  Dev     ●
```

Or as tabs if a sidebar feels too cramped — decide during implementation.

The **session overview** (bottom or separate tab) lists every project × role combination.
Each row:

```
[project name]  [role name]  [● Active | ○ Idle]   [toggle button]
```

Clicking the toggle flips active ↔ idle for that role on that project. Active rows are
visually distinct (accent color or bold).

**Empty state:** "No sessions registered. Select a project and mark a role as active."

### Why manual toggle, not process detection?

Process detection (reading `ps`, matching working directories to project paths) is the
right end-game but involves platform differences, timing, and unreliable heuristics.
Manual toggle proves the concept — does seeing this panel reduce wrong-chat errors? —
without that complexity. Detection can be layered on later as a `session-tracking` epic.

## Out of Scope

- Editing role instructions through the UI
- Deleting projects or roles through the UI
- Automatic process detection
- Any persistence of session state (sessions are always empty on restart)
- Reading `CLAUDE.md` content into Foreman (may come later under a `context-docs` feature)

## Issues

3. Project registration dialog + role discovery (`enhancement` + `core`)
4. Session panel — manual active/idle toggle per project × role (`enhancement` + `ui`)

Issue 4 can start once issue 3 gives it real data to show.

## Open Questions

- If two projects share a role name but have meaningfully different instructions
  (e.g. both have "Dev" but one is Java, one is Python), deduplication by name loses
  the difference. For now: first-registered wins, document the limitation. A future
  issue can add per-project role overrides.
