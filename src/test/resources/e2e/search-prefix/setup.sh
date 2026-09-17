# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote proj
clone_git remote proj-fork-alice
clone_git remote proj-fork-alice-branch-feature
clone_git remote unrelated
