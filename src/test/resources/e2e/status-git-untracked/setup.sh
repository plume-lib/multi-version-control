# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote clone
echo "new" > "$HOME/clone/untracked.txt"
mkdir "$HOME/clone/untracked-dir"
echo "new" > "$HOME/clone/untracked-dir/another.txt"
