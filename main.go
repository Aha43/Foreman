package main

import (
	"fmt"
	"os"
)

const usage = `foreman — organize terminals by project, on top of tmux

usage:
  foreman list                     all projects and their terminals
  foreman <project> new <role>     new terminal in the project (runs role preset, if any)
  foreman <project> go <role>      jump to that terminal (from anywhere)
  foreman <project> list           terminals in the project
  foreman <project> adopt <role>   pull the current terminal into the project
  foreman <project> done [role]    close one terminal, or the whole project
  foreman <project> pin|unpin      mark the project as priority

config: ~/.config/foreman/config.toml
  [role.planner]
  cmd = "claude --permission-mode plan"

  [project.A]
  root = "~/code/project-a"
`

func main() {
	args := os.Args[1:]
	if len(args) == 0 || args[0] == "help" || args[0] == "-h" || args[0] == "--help" {
		fmt.Print(usage)
		return
	}
	if args[0] == "list" {
		fail(cmdListAll())
		return
	}

	project := args[0]
	if len(args) < 2 {
		fmt.Print(usage)
		os.Exit(2)
	}
	verb := args[1]
	arg := ""
	if len(args) > 2 {
		arg = args[2]
	}

	switch verb {
	case "new":
		requireArg(verb, arg)
		fail(cmdNew(project, arg))
	case "go":
		requireArg(verb, arg)
		fail(cmdGo(project, arg))
	case "list":
		fail(cmdList(project))
	case "adopt":
		requireArg(verb, arg)
		fail(cmdAdopt(project, arg))
	case "done":
		fail(cmdDone(project, arg))
	case "pin":
		fail(cmdPin(project, true))
	case "unpin":
		fail(cmdPin(project, false))
	default:
		fmt.Fprintf(os.Stderr, "unknown command %q\n\n%s", verb, usage)
		os.Exit(2)
	}
}

func requireArg(verb, arg string) {
	if arg == "" {
		fmt.Fprintf(os.Stderr, "usage: foreman <project> %s <role>\n", verb)
		os.Exit(2)
	}
}

func fail(err error) {
	if err != nil {
		fmt.Fprintln(os.Stderr, "foreman:", err)
		os.Exit(1)
	}
}
