# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote clone
# A branch that has no upstream.
git -C clone switch -q -c local-only
