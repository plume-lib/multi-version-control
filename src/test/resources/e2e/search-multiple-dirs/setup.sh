# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
mkdir "$HOME/first" "$HOME/second" "$HOME/third"
clone_git remote first/alpha
clone_git remote second/beta
clone_git remote third/not-searched
