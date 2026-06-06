# Changelog

All notable changes to Foreman are documented here.

## [Unreleased]

### Fixed
- Briefing role doc path now resolves correctly: `Role` stores `sourceFile` (the `.md` filename) at discovery time; `BriefingService` uses it directly instead of guessing from the role name
- `gh issue list` now works on macOS Homebrew installs: binary path resolved via `which gh` at startup instead of relying on the process PATH

### Added
- "Brief" button on each session row: generates a handover briefing (role, project, open issues via `gh`, design docs) in a copyable dialog
- Right-click context menu on project list with "Remove project…" option; confirmation dialog before removal
- Removing a project drops its sessions from `SessionRegistry` and refreshes the session panel

### Added
- Session panel: "Sessions" tab showing all project × role pairs with active/idle toggle; active rows shown bold with accent status indicator
- `Session` domain record (in-memory, not persisted) and `SessionRegistry` with change notification
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
