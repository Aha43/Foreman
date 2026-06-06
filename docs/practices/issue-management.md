# Issue Management

## Issue shape

Every issue must have:

- **What** — a concrete description of the change
- **Why** — the motivation or user problem it solves
- **Suggested behavior** — the desired outcome
- **Out of scope** — what this issue explicitly does NOT cover
- **Notes** — implementation thoughts, edge cases, open questions

## Labels

Apply exactly two labels to every issue:

1. **Type:** `enhancement` or `bug`
2. **Feature area:** one of the labels defined in `docs/roles/planning.md`

## Epic structure

An epic is a collection of issues under one feature area label that together deliver a coherent capability.

Before creating issues for an epic:
- Write `docs/features/<name>/design.md`
- Resolve product decisions (naming, flow, model shape) in that doc or as issue comments
- Create issues in dependency order — prerequisites first

## Cross-references

Every issue in an epic should have a "Related issues" section listing sibling issues by number.

## One issue at a time

The dev chat works one issue at a time and stops for go-ahead before the next. Do not assign multiple issues to a sprint without explicit agreement.

## Minor gaps

During implementation, the dev chat may create single focused issues for:
- A missing guard or validation
- An obvious follow-on behavior spotted while coding
- A bug found in passing

These do not need a design doc. They should still have the full issue shape and correct labels.
