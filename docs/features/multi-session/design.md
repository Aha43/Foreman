# Design: Multi-Session per Role

## Motivation

A single role can legitimately need more than one concurrent session. A Dev Chat might
have one terminal deep in a refactor while a second explores a side branch. A Planning
Chat might be mid-thought on one topic while a second is being briefed on another.

The current model enforces one session per (project, role) pair. Lifting that constraint
requires both a domain change and an adaptive UI that stays uncluttered at the common
case (one session) while scaling cleanly to multiple.

## Adaptive Row Behavior

The session panel row for a role adapts to how many sessions are running:

**0 sessions (never launched, or all closed):**
```
Dev                    [Launch]
```
Identical to the current UI. No change in appearance.

**1 session running:**
```
Dev  ● Running         [Focus] [+ Another]
```
Same as today, with a `[+ Another]` button added at the right.

**2+ sessions running:**
```
Dev (2)
  ● Running            [Focus]
  ● Running            [Focus] [+ Another]
```
The role label becomes a group header showing the session count. Sub-rows appear beneath
it, one per session. The `[+ Another]` button sits on the last sub-row only.

**Collapsing back:**
- 2 → 1: the group header and sub-rows disappear; the single-session row returns.
- 1 → 0: the `[+ Another]` button disappears; the row returns to the default Launch state.

These transitions happen live as TTY state changes (same mechanism as the existing
Running/Stopped detection on window focus).

## Terminal Title Convention

Single session (unchanged):
```
Foreman · Project / Dev
```

Additional sessions get a `#N` suffix, counting from 2:
```
Foreman · Project / Dev #2
Foreman · Project / Dev #3
```

The first session keeps no suffix so existing behaviour is unchanged for the common case.

## Domain Model Change

`SessionRegistry` currently maps `(projectId, roleId) → Session`.

Change to `(projectId, roleId) → List<Session>`.

Each `Session` in the list carries:
- its existing fields (`id`, `projectId`, `roleId`, `tty`)
- a 1-based **index** within the list (assigned at launch, used for the `#N` title suffix)

The index is positional — if session #2 is closed, session #3 does not renumber to #2.
Renumbering would change the terminal title of a live window mid-session. Accepted
trade-off: gaps in numbering are fine.

`TerminalLauncherService.launch()` receives the session index and applies the suffix
when index > 1.

## Human Row

The implicit Human row is exempt. At most one shell per project makes sense and the
`[+ Another]` button is not shown there.

## Out of Scope

- User-assigned labels for individual sessions
- Collapsible sub-rows (all sub-rows are always visible)
- Persisting session index across Foreman restarts (TTY state is already in-memory only)
- Brief button per sub-row (briefing is per role, not per session instance — the Brief
  button stays on the role-level row or the single-session row as today)

## Issues

A. Support multiple sessions per role in `SessionRegistry` (`enhancement` + `core`)
B. Session panel: adaptive multi-session row rendering (`enhancement` + `ui`)

Issue B depends on A.
