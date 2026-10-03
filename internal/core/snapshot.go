package core

import (
	"sort"
	"strconv"
)

// SnapshotSchema versions the snapshot's JSON shape. Consumers live in
// other binaries (the explorer, possibly on another OS, issue #97), so a
// breaking change to the shape bumps this.
const SnapshotSchema = 1

// Snapshot is everything a front end needs to render foreman in one read:
// the live projects with each participant's observed state, plus the
// config-derived choices (roles to start, dormant projects). One call
// instead of several tmux round-trips per participant — the difference that
// matters when every call crosses a boundary like wsl.exe (issue #97).
type Snapshot struct {
	Schema   int               `json:"schema"`
	Projects []SnapshotProject `json:"projects"`
	Dormant  []string          `json:"dormant"`
	Roles    []string          `json:"roles"`
}

type SnapshotProject struct {
	Name         string                `json:"name"`
	Pinned       bool                  `json:"pinned"`
	Attached     bool                  `json:"attached"`
	Clients      int                   `json:"clients"`
	Participants []SnapshotParticipant `json:"participants"`
}

type SnapshotParticipant struct {
	ID       string `json:"id"`       // immutable tmux window id
	Role     string `json:"role"`     // RoleName: @fm_role, else window name
	State    State  `json:"state"`    // StateOf — the one classifier (#38)
	Status   string `json:"status"`   // WindowStatus, for humans
	Activity int64  `json:"activity"` // unix time of last activity, 0 if unknown
	// Previous is the state recorded by the last watching snapshot, set
	// only when this one watches; "" means never recorded.
	Previous State `json:"previous,omitempty"`
}

// TakeSnapshot observes every project. With watch set it is also the
// watcher's memory (issue #5): each participant's previously recorded
// state is reported as Previous and the fresh state recorded in its place,
// so the caller can spot transitions (into waiting, say) without any state
// of its own — exactly what the explorer's watch pass does in-process.
func TakeSnapshot(cfg Config, watch bool) (Snapshot, error) {
	projects, err := ListProjects()
	if err != nil {
		return Snapshot{}, err
	}
	s := Snapshot{
		Schema:   SnapshotSchema,
		Projects: []SnapshotProject{},
		Dormant:  DormantProjects(cfg, projects),
		Roles:    RoleChoices(cfg),
	}
	for _, p := range projects {
		sp := SnapshotProject{Name: p.Name, Pinned: p.Pinned, Attached: p.Attached, Clients: p.Clients, Participants: []SnapshotParticipant{}}
		for _, w := range p.Windows {
			st := StateOf(cfg, w)
			activity, _ := strconv.ParseInt(w.Activity, 10, 64)
			part := SnapshotParticipant{ID: w.ID, Role: w.RoleName(), State: st, Status: WindowStatus(w), Activity: activity}
			if watch {
				part.Previous = LastState(w)
				if st != part.Previous {
					RecordState(w, st)
				}
			}
			sp.Participants = append(sp.Participants, part)
		}
		s.Projects = append(s.Projects, sp)
	}
	return s, nil
}

// RoleChoices is what a new participant may start as: every configured role
// plus shell — unknown roles get a plain shell, so it is always valid.
func RoleChoices(cfg Config) []string {
	roles := make([]string, 0, len(cfg.Roles)+1)
	for r := range cfg.Roles {
		roles = append(roles, r)
	}
	if _, ok := cfg.Roles["shell"]; !ok {
		roles = append(roles, "shell")
	}
	sort.Strings(roles)
	return roles
}

// DormantProjects is the configured projects with no live session, sorted —
// never nil, so it marshals as [] rather than null.
func DormantProjects(cfg Config, projects []Project) []string {
	live := map[string]bool{}
	for _, p := range projects {
		live[p.Name] = true
	}
	dormant := []string{}
	for name := range cfg.Projects {
		if !live[name] {
			dormant = append(dormant, name)
		}
	}
	sort.Strings(dormant)
	return dormant
}
