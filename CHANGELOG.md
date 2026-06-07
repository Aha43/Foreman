# Changelog

All notable changes to Foreman are documented here.

## [Unreleased]

### Added
- Icon infrastructure: `ForemanUiHelper` with `iconButton()` / `iconOnlyButton()` / `icon()` / `applyDense()` factory methods; 8 Tabler Icons SVGs downloaded to `src/icons/` and bundled into the JAR
- Dense mode: Settings dialog checkbox toggles icon-only buttons app-wide; setting persisted to `~/.foreman/settings.json`; `ForemanSettings` gains `Boolean dense` field (null-safe for old JSON)
- Toolbar buttons (Register Project, Settings, Exit) and session panel buttons (Launch, Focus, Brief) now show SVG icons via `ForemanUiHelper.iconButton()`; text labels are hidden in dense mode, leaving only the icon
- Keyboard shortcuts wired on main window root pane: ⌘R Register Project, ⌘, Settings, ⌘Q Exit, ⌘/ Shortcuts dialog, ⌘W Close; session panel row shortcuts L/F/B (Launch, Focus, Brief) operate on the clicked row; ShortcutsDialog accessible via toolbar keyboard button or ⌘/

### Fixed
- Sessions no longer show as Running after Foreman restart: TTY map is now in-memory only; macOS recycles /dev/ttysN devices so persisting them caused stale matches against unrelated terminals on restart
- Closing a terminal no longer leaves the row stuck on "Focus": Foreman reloads session state whenever its window regains focus, detecting closed TTYs and flipping rows back to "Launch"
- Focus and exists now identify terminal tabs by TTY device (`/dev/ttysN`) instead of window title; Claude Code overrides the visible title via escape sequences so title-based matching was always broken after Claude started; TTY is captured from AppleScript at launch time and persisted to `~/.foreman/tty-map.properties` so it survives Foreman restarts
- Terminal no longer fails to launch after the Claude auto-start change: the shell command containing `"$(cat ...)"` was embedding double quotes inside an AppleScript double-quoted string, terminating it early; fix writes a real `~/.foreman/launch.sh` script and has AppleScript run `bash '/path/to/launch.sh'` instead, keeping double quotes safely inside the bash script

### Added
- Launch now starts Claude Code automatically with the briefing as its initial message: writes briefing to `~/.foreman/last-briefing.txt`, then runs `claude "$(cat ~/.foreman/last-briefing.txt)"` in the new terminal window; `claude` binary resolved via `which claude` at startup; clipboard copy kept as fallback

### Added
- Session panel Launch/Focus buttons: "Launch" opens a titled Terminal.app window and copies the briefing to the clipboard; "Focus" brings the existing window to front; status shows `● Running` / `○ Stopped`
- Startup restore: sessions whose Terminal.app windows survived a Foreman restart are automatically marked Running on launch
- Non-macOS graceful fallback: when `TerminalLauncher.isSupported()` is false the panel keeps the old manual Active/Idle toggle
- `SessionRegistry.setRunning()` to set session state directly (used by launch action and startup restore)

### Added
- `TerminalLauncher` interface with `MacOsTerminalLauncher` (osascript) and `NoOpTerminalLauncher` (stub) implementations
- `MacOsTerminalLauncher`: `launch` opens a titled Terminal.app window and copies the briefing to the clipboard; `focus` brings an existing window to front; `exists` checks for a window by label
- Window title convention: `Foreman · [Project] / [Role]`; single-quote escaping for shell-safe AppleScript embedding
- Platform selection in `ForemanMain`: macOS gets `MacOsTerminalLauncher`, all others get `NoOpTerminalLauncher`

### Changed
- Briefing text is now imperative: opens with "You are the <role> for <project>", lists files to read (CLAUDE.md, role doc, design docs), then gives a direct task directive — single-issue: "Your task: implement issue #N", multi-issue: numbered list with "Confirm with the user which to start with", no issues: fallback message; closes with "Do not start until you have read the files and issue(s) listed above."

### Added
- Settings persistence: `ForemanSettings` / `ForemanSettingsRepository` / `ForemanSettingsService` — persisted to `~/.foreman/settings.json`
- Settings dialog (reachable from toolbar) with default project directory field and Browse button
- Register Project file chooser now opens in the saved default directory; updates it automatically after each successful registration
- Main toolbar (non-floatable `JToolBar`) with Register Project, Settings (stub), and Exit buttons; Register Project moved from the project list panel to the toolbar

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
