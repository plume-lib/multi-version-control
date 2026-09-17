# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote clone
echo "new" > "$HOME/clone/added.txt"
git -C "$HOME/clone" add added.txt
echo "second line" >> "$HOME/clone/file.txt"
git -C "$HOME/clone" add file.txt
echo "third line" >> "$HOME/clone/file.txt"
