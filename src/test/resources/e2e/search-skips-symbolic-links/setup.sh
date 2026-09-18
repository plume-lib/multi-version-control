# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
mkdir "$HOME/real"
clone_git remote real/alpha
ln -s "$HOME/real" "$HOME/link-to-real"
