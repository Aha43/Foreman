# Foreman Vision

Foreman is a Java/Swing desktop application for managing AI development teams.

An AI team is a set of Claude Code sessions, each in a defined role (Planner, Dev, Dogfooder, etc.), working on a shared project. Foreman is the control plane: it does not host the chats — it manages the configuration that makes each session effective and provides a clear overview of what is happening across all projects and sessions.

## Problem

When working with multiple Claude Code roles on one or more projects, the practical problems are:

- No central view — open terminal windows give no indication of which role or project they belong to
- Wrong-chat errors — typing a message intended for the Planner into the Dev chat is easy when switching between sessions
- Context drift — role instructions and CLAUDE.md files must be manually maintained and loaded into each session
- Scaling — two projects means six or more concurrent sessions, making the above problems worse

## What Foreman Does

Foreman manages three kinds of entities:

**Roles** — reusable definitions of an AI team member: name, description, and the full instruction document the session should load on startup. A Role library is shared across projects.

**Projects** — a local project path with associated metadata and a Team: the set of Roles assigned to it. Each project also has its own context documents (CLAUDE.md, design docs) that Foreman helps maintain.

**Sessions** — running Claude Code instances, each associated with a Role and a Project. Foreman tracks which sessions are alive and displays them in a dashboard so the user always knows where they are.

## Core UI Shape

- **Project list** — all known projects, each showing its active sessions and their roles
- **Role library** — create, edit, and delete role definitions
- **Team builder** — assign roles to a project and launch sessions from there
- **Session overview** — at-a-glance status for every active session across all projects

## What Foreman Does NOT Do

- Foreman does not host or embed chat windows
- Foreman does not send messages to Claude on the user's behalf
- Foreman does not replace CLAUDE.md — it helps generate and maintain it

## The Meta-Goal

Foreman is developed using the same three-role AI team pattern it is designed to support. Once functional enough, Foreman will manage its own development backlog — tracking issues, roles, and sessions for the Foreman project itself.

## Development Model

Development is issue-driven. Every change starts from a GitHub issue. The project uses three Claude Code roles:

- **Planning Chat** — designs features, scopes epics, creates issues
- **Dev Chat** — implements issues, runs tests, commits
- **Dogfooding Chat** — uses Foreman as a real user, surfaces gaps

Small iterations are preferred over large speculative implementations. The goal is disciplined AI-assisted development, not uncontrolled vibe coding.

## Guiding Principle

Foreman should evolve through small, working increments. Prefer a modest feature that works and teaches something over a broad architecture that is not yet exercised.
