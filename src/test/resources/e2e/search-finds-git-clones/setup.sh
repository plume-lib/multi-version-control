# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote alpha
mkdir -p "$HOME/deeply/nested/directory"
clone_git remote deeply/nested/directory/beta
mkdir "$HOME/not-a-clone"
