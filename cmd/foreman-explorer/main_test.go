package main

import "testing"

func TestShellQuote(t *testing.T) {
	cases := map[string]string{
		"plain":                    "'plain'",
		"My Project":               "'My Project'",
		"x; rm -rf ~":              "'x; rm -rf ~'",
		`back"quote`:               `'back"quote'`,
		"it's":                     `'it'\''s'`,
		"$(whoami)":                "'$(whoami)'",
		"/Users/First Last/bin/fm": "'/Users/First Last/bin/fm'",
	}
	for in, want := range cases {
		if got := shellQuote(in); got != want {
			t.Errorf("shellQuote(%q) = %s, want %s", in, got, want)
		}
	}
}
