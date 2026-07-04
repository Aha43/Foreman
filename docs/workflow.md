# Development workflow

> Status: **current model (2026-07-04).** Deliberately lightweight for a solo developer, adopted
> from the NamWeb workflow (`NamWeb/docs/ops/workflow.md`) and trimmed to fit a local CLI tool:
> foreman has no production environment — a "release" is a git tag and a GitHub Release, and the
> blast radius of a bad merge is one laptop. Companion to [RELEASING.md](./RELEASING.md).

## Issues are the unit of work

Every non-trivial change is a **GitHub issue** before it is code. The issue holds the motivation
and the design discussion; the commit/PR closes it (`Closes #N`). Small drive-by fixes may skip
the issue, but anything worth a CHANGELOG entry is worth an issue.

Labels: the GitHub defaults (`bug`, `enhancement`, `documentation`, …) plus domain labels —
`core` (tmux layer, `internal/core`), `cli` (command surface), `explorer` (menu bar app),
`config` (config.toml / role presets), `observation` (the observation-layer epic), `infra`
(build, tooling, CI).

## Sprints are milestones

Work is grouped into **sprints, one GitHub milestone each** — either numbered
(`Sprint N — Theme`) or thematic (`Observation layer`, `Polish 1`). The milestone description
states the goal in a sentence or two. A sprint is done when its issues are closed; the end of a
sprint is the natural moment to cut a release (see [RELEASING.md](./RELEASING.md)).

Not everything needs a sprint — off-cycle fixes ride on `main` and fold into the next release.

## Branches and merging

1. Branch off `main` per issue (`feature/<slug>` or `fix/<slug>`).
2. Develop locally; `go build ./... && go vet ./...` (and tests, as they arrive) must pass.
3. Open a PR and merge to `main`. Solo, so review is self-review — the PR still earns its keep
   as the changelog of record and the thing a Codex/Claude review can be pointed at.
4. Every merged non-chore PR adds its entry under `## [Unreleased]` in `CHANGELOG.md`.

## Evolving this

Current gaps, to add when they start paying for themselves:

- **CI** — a GitHub Actions `check` job (build/vet/test) as the PR gate, then branch protection
  on `main` requiring it.
- **Release automation** — a `release.yml` that publishes a GitHub Release (with prebuilt
  binaries) from a `v*` tag, as NamWeb does.
