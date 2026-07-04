# Releasing foreman

foreman follows [Semantic Versioning](https://semver.org/). The **annotated git tag is the single
source of truth** for the version — there is no version file to bump. Pre-1.0 (`0.MINOR.PATCH`):
minor = features (breaking changes allowed), patch = fixes. Reserve `1.0.0` for when the CLI
surface and the tmux state contract (`@fm_*` user options) are stable.

## When to release (cadence)

**Merging is not releasing.** Users build from `main` at will; a *version* is a deliberate
milestone you cut when a batch of work is worth naming and changelogging. A **sprint is the
natural unit** for that.

| Bump | When | Example |
| --- | --- | --- |
| **Minor** (`0.x.0`) | End of a sprint / batch of features — the default | `0.1.0 → 0.2.0` |
| **Patch** (`0.x.y`) | An off-cycle fix released on its own between sprints | `0.2.0 → 0.2.1` |
| **Major** (`x.0.0`) | Not used pre-1.0 — breaking changes ride in minors until 1.0.0 | — |

Entries accumulate under `## [Unreleased]` in `CHANGELOG.md` as PRs merge; cutting a release just
renames that section. A single off-cycle fix only earns a patch release if it's worth
*announcing* — otherwise let it ride on `main` and fold it into the next sprint's minor.

## Cutting a release

1. **Update `CHANGELOG.md`** on `main`: rename `## [Unreleased]` to `## [X.Y.Z] - YYYY-MM-DD`,
   add a fresh empty `## [Unreleased]` above it, and update the link definitions at the bottom
   (`[Unreleased]` compare range + a new `[X.Y.Z]` link). Commit.

2. **Sanity gate**: `go build ./... && go vet ./... && go test ./...` green on the release commit.
   Before a minor, point a code review (Claude/Codex) at the diff since the previous tag.

3. **Tag and push** (annotated):

   ```bash
   git tag -a vX.Y.Z -m "vX.Y.Z"
   git push origin main vX.Y.Z
   ```

4. **Publish the GitHub Release** with the changelog section as notes:

   ```bash
   gh release create vX.Y.Z --title "vX.Y.Z" --notes "<the [X.Y.Z] CHANGELOG section>"
   ```

   (Automate via a `release.yml` on `v*` tags — with prebuilt binaries — when it earns its keep;
   see the *Evolving this* list in [workflow.md](./workflow.md).)
