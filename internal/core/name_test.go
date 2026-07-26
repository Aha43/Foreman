package core

import "testing"

func TestValidateProjectName(t *testing.T) {
	good := []string{"A", "my-app", "My Project", "web2", "a_b", "renamed"}
	for _, n := range good {
		if err := ValidateProjectName(n); err != nil {
			t.Errorf("ValidateProjectName(%q) = %v, want nil", n, err)
		}
	}
	bad := []string{
		"",                       // empty
		"done", "list", "rename", // reserved verbs and globals
		"my.app", "a:b", // tmux targets split on . and :
		"x[1]",         // would corrupt the config header
		"a\nb", "a\tb", // control characters break line parsing
		"-flag", "__ticker", // flag-alike and plumbing prefix
	}
	for _, n := range bad {
		if err := ValidateProjectName(n); err == nil {
			t.Errorf("ValidateProjectName(%q) = nil, want error", n)
		}
	}
}
