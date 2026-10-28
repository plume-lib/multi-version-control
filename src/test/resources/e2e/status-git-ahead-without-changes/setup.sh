# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote clone
git -C clone commit -q --allow-empty -m "A commit that changes nothing"
