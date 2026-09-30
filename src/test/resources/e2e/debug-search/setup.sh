# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
mkdir -p "$HOME/searched/ignored"
clone_git remote searched/alpha
clone_git remote searched/ignored/skipped
