# Design: Core Domain Model

## Motivation

The skeleton app shows a placeholder label. No entities exist, nothing is persisted.
Every subsequent feature — role library, team builder, session overview — depends on
a shared domain model and a workspace file it can read and write. This epic establishes
that foundation before any meaningful UI panel can be built.

## Entities

### Role

A reusable definition of an AI team member. Roles live in a shared library and are
referenced by Teams across multiple projects.

```
Role
  id:           String (UUID, stable identifier)
  name:         String (e.g. "Planner", "Dev", "Dogfooder")
  instructions: String (full markdown text loaded into the session on startup)
```

### RoleAssignment

A role as assigned within a specific team. Keeps a reference to the Role by id and
optionally overrides the display label for that project context.

```
RoleAssignment
  roleId: String (references Role.id)
  label:  String (optional display override, defaults to Role.name)
```

### Team

The set of roles assigned to a project. One Team per Project.

```
Team
  assignments: List<RoleAssignment>
```

### Project

A local project with a path and a team.

```
Project
  id:          String (UUID)
  name:        String
  path:        String (absolute local filesystem path)
  description: String (optional, may be empty)
  team:        Team
```

### ForemanWorkspace

Top-level container. Holds the role library and the project list.

```
ForemanWorkspace
  roles:    List<Role>
  projects: List<Project>
```

## Design Decisions

**Roles are shared, not per-project copies.**
A "Dev" role definition is written once and referenced by any project that needs it.
This avoids drift where the same role evolves differently across project copies.
Teams reference roles by `roleId`; if a role is deleted, its assignments become dangling
(the UI will need to surface this eventually, but that is out of scope here).

**Single flat JSON file.**
`~/.foreman/workspace.json` holds the entire workspace. Simple, inspectable, no migration
complexity at this stage. Dev mode uses `~/.foreman/dev/workspace.json`.

**IDs are UUIDs assigned at creation.**
Stable across renames. `java.util.UUID.randomUUID().toString()`.

**No command layer yet.**
Mutations will go through `ForemanWorkspaceService` methods, but the service will be
minimal — load, save, and simple accessors. A richer command layer can emerge when
multiple panels need to trigger the same mutations.

## Persistence

`ForemanWorkspaceRepository` — two methods:
- `load(Path)` → `ForemanWorkspace` (returns empty workspace if file absent)
- `save(ForemanWorkspace, Path)`

Jackson with `ObjectMapper` configured for pretty-print and `JavaTimeModule` if timestamps
are added later. Field order: `roles` before `projects`.

## Service

`ForemanWorkspaceService` — holds the loaded workspace in memory, exposes it to UI panels.
Startup: load from `~/.foreman/workspace.json` (or dev path). No auto-save yet; save is
explicit.

## UI Change

Replace the placeholder label with a two-panel split:

- **Left panel** — project list (`JList<Project>`) showing project names. Selects first
  project on load if any exist.
- **Right panel** — project detail (read-only): name, path, description, and a list of
  role assignments by label.

Empty state (no projects): left panel shows "No projects yet." Right panel is blank.

This is the minimum UI needed to confirm that persistence works end-to-end and that the
model loads correctly into a Swing panel.

## Out of Scope

- Adding, editing, or deleting projects or roles through the UI
- Role library panel
- Team builder UI
- Session tracking
- Any form of MCP integration

## Issues

1. Define domain entities and JSON persistence (`enhancement` + `core`)
2. Project list panel — replace placeholder with split view (`enhancement` + `ui`)

Issue 2 depends on issue 1.

## Open Questions

- Should `path` be validated at load time (warn if directory does not exist)?
  Deferred — flag it visually in the UI later, do not block load.
