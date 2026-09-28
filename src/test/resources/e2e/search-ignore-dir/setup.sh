# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote kept
mkdir "$HOME/ignored" "$HOME/also-ignored"
clone_git remote ignored/skipped
clone_git remote also-ignored/skipped-too
