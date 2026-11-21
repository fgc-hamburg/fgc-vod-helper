# Every action in this repo goes through here. Run `make` to list targets.
# CI calls the same targets, so local and CI behave the same.

ifeq ($(OS),Windows_NT)
# Windows builds of make only accept a full path for SHELL (anything else falls back to cmd.exe).
# Until SHELL is set they use Git Bash's sh.exe, which we use to find bash's short (space-free) path.
SHELL := $(shell cygpath -ms "$$(command -v bash)")
else
SHELL := bash
endif
.DEFAULT_GOAL := help

GRADLE := ./gradlew
DIST := dist
APP := fgc-vod-helper
# Version for package/release: app.version in gradle.properties, unless given on the
# command line (make package VERSION=x.y.z). A VERSION environment variable is ignored.
APP_VERSION := $(shell sed -n 's/^app\.version[[:space:]]*=//p' gradle.properties | tr -d '[:space:]')
override VERSION := $(if $(filter command line,$(origin VERSION)),$(VERSION),$(APP_VERSION))
BINARIES := app/build/compose/binaries/main

# Local keys from .env (created by `make setup`) are passed to Gradle as
# environment variables. In CI they come from GitHub Actions secrets instead.
ifneq (,$(wildcard .env))
include .env
export $(shell sed -n 's/^\([A-Za-z_][A-Za-z0-9_]*\)=.*/\1/p' .env)
endif

.PHONY: help setup run check test build package clean version release worktree screenshot record pr-media

help: ## List targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "} {printf "  \033[36m%-11s\033[0m %s\n", $$1, $$2}'

# --- Develop ---------------------------------------------------------------

setup: ## Create .env from .env.example (keeps existing values, adds missing keys)
	@if [ ! -f .env ]; then cp .env.example .env; echo "Created .env — fill in the keys (see comments inside)."; \
	else added=0; \
		for key in $$(sed -n 's/^\([A-Za-z_][A-Za-z0-9_]*\)=.*/\1/p' .env.example); do \
			grep -q "^$$key=" .env || { echo "$$key=" >> .env; echo "Added $$key to .env"; added=1; }; \
		done; \
		[ $$added = 1 ] || echo ".env is up to date."; \
	fi
	@missing=$$(sed -n 's/^\([A-Za-z_][A-Za-z0-9_]*\)=$$/\1/p' .env | tr '\n' ' '); \
		[ -z "$$missing" ] || echo "Still empty: $$missing"

run: ## Run the app locally
	$(GRADLE) run

check: ## Compile, test and lint — what CI runs, must pass before a PR
	$(GRADLE) check --stacktrace

test: ## Run unit tests only
	$(GRADLE) test

build: ## Compile the app without packaging
	$(GRADLE) assemble

clean: ## Remove build outputs and dist/
	$(GRADLE) clean
	rm -rf $(DIST)

# --- Package and release ---------------------------------------------------

package: ## Build this OS's installer into a fresh dist/ — make package [VERSION=1.2.3] (default: app.version in gradle.properties)
	@test -n "$(VERSION)" || { echo "No VERSION given and no app.version in gradle.properties"; exit 1; }
	rm -rf $(DIST) $(BINARIES)
	$(GRADLE) packageDistributionForCurrentOS -Papp.version=$(VERSION)
	@mkdir -p $(DIST)
	@n=0; while IFS= read -r -d '' f; do cp "$$f" "$(DIST)/$(APP)-$(VERSION).$${f##*.}"; n=$$((n+1)); done < \
		<(find $(BINARIES) \( -name '*-$(VERSION).dmg' -o -name '*-$(VERSION).msi' \) -print0); \
		[ $$n -eq 1 ] || { echo "Expected 1 installer in $(BINARIES), found $$n"; exit 1; }
	@ls -1 $(DIST)

version: ## Print the latest release tag
	@git describe --tags --abbrev=0 --match 'v*' 2>/dev/null || echo "No release yet"

release: ## Tag main and push it; CI builds installers and publishes the GitHub Release — make release [VERSION=1.2.3] (default: app.version)
	@bash scripts/release.sh "$(VERSION)"

# --- Ticket workflow -------------------------------------------------------

worktree: ## Create a worktree for a ticket — make worktree TICKET=FGC-1 BRANCH=<Linear branch name>
	@test -n "$(TICKET)" -a -n "$(BRANCH)" || { echo "Set TICKET and BRANCH"; exit 1; }
	git fetch origin
	git worktree add .worktrees/$(TICKET) -b $(BRANCH) origin/main
	@echo "Worktree: .worktrees/$(TICKET)"

SECONDS ?= 20

screenshot: ## Screenshot an app window into media/<TICKET>/ (macOS) — make screenshot TICKET=FGC-1 NAME=01-setup
	@test -n "$(TICKET)" -a -n "$(NAME)" || { echo "Set TICKET and NAME"; exit 1; }
	@mkdir -p media/$(TICKET)
	screencapture -o -w media/$(TICKET)/$(NAME).png
	@echo "Saved media/$(TICKET)/$(NAME).png"

record: ## Record the screen and save a GIF into media/<TICKET>/ (macOS) — make record TICKET=FGC-1 NAME=02-flow SECONDS=15
	@test -n "$(TICKET)" -a -n "$(NAME)" || { echo "Set TICKET and NAME"; exit 1; }
	@mkdir -p media/$(TICKET)
	@echo "Recording $(SECONDS)s of the main screen..."
	ffmpeg -loglevel error -y -f avfoundation -capture_cursor 1 -framerate 30 -i "1:none" -t $(SECONDS) media/$(TICKET)/.$(NAME).mov
	ffmpeg -loglevel error -y -i media/$(TICKET)/.$(NAME).mov \
		-vf "fps=12,scale=960:-1:flags=lanczos,split[a][b];[a]palettegen[p];[b][p]paletteuse" media/$(TICKET)/$(NAME).gif
	@rm -f media/$(TICKET)/.$(NAME).mov
	@echo "Saved media/$(TICKET)/$(NAME).gif"

pr-media: ## Upload media/<TICKET>/ to the pr-media branch and print Markdown links — make pr-media TICKET=FGC-1
	@test -n "$(TICKET)" || { echo "Set TICKET"; exit 1; }
	@bash scripts/pr-media.sh "$(TICKET)"
