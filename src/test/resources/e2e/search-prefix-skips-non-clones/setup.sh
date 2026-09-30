# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote proj
mkdir "$HOME/proj-not-a-clone"
touch "$HOME/proj-a-file"
