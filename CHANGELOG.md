# Changelog

All notable changes to Foreman are documented here.

## [Unreleased]

### Added
- "Register Project" button in the project list panel; opens a dialog to pick a directory and name
- Role discovery: scans `docs/roles/*.md` under the project root, extracts role names from headings, falls back to filename-derived name
- Discovered roles are deduplicated against the library by name (first-registered wins)
- `ProjectRegistrationService` orchestrates discovery, dedup, team assembly, and workspace save

### Added
- Project list panel: two-panel split view replacing the placeholder label; left panel lists projects, right panel shows selected project details (name, path, description, team assignments)
- Empty state ("No projects yet.") when no projects are loaded
- Domain entities: `Role`, `RoleAssignment`, `Team`, `Project`, `ForemanWorkspace`
- `ForemanWorkspaceRepository` — load/save workspace as pretty-printed JSON via Jackson
- `ForemanWorkspaceService` — loads workspace on startup from `~/.foreman/workspace.json` (or `~/.foreman/dev/workspace.json` in dev mode)
- `--dev` flag on the main entry point selects the dev workspace path

## [0.1.0] - 2026-06-06

### Added
- Initial project scaffold: Java/Swing skeleton, makefile, role definitions, docs structure
