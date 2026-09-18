# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote clone
echo "second line" >> "$HOME/clone/file.txt"
