# Thin conveniences over the go toolchain, matching the NamWeb/NamDesktop
# make muscle memory. `make install` puts foreman (+ fm symlink) and
# fm-explorer into BIN (default ~/.local/bin).

BIN ?= $(HOME)/.local/bin

.PHONY: help build install uninstall check fmt vet test clean

help:
	@echo "foreman make targets:"
	@echo "  make build      Build ./foreman and ./foreman-explorer"
	@echo "  make install    Build and install into $(BIN) (foreman, fm symlink, fm-explorer)"
	@echo "  make uninstall  Remove the installed binaries from $(BIN)"
	@echo "  make check      gofmt + go vet + go test (the PR gate, docs/workflow.md)"
	@echo "  make clean      Remove built binaries"

build:
	go build -o foreman ./cmd/foreman
	go build -o foreman-explorer ./cmd/foreman-explorer

install: build
	mkdir -p $(BIN)
	install foreman $(BIN)/foreman
	install foreman-explorer $(BIN)/fm-explorer
	ln -sf $(BIN)/foreman $(BIN)/fm

uninstall:
	rm -f $(BIN)/foreman $(BIN)/fm $(BIN)/fm-explorer

check:
	@fmt_out=$$(gofmt -l .); if [ -n "$$fmt_out" ]; then echo "gofmt needed:"; echo "$$fmt_out"; exit 1; fi
	go vet ./...
	go test ./...

fmt:
	gofmt -w .

vet:
	go vet ./...

test:
	go test ./...

clean:
	rm -f foreman foreman-explorer
