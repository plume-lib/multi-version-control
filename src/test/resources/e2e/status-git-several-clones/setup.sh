# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote clean
clone_git remote dirty
clone_git remote ahead
echo "second line" >> "$HOME/dirty/file.txt"
echo "new" > "$HOME/dirty/untracked.txt"
echo "second line" >> "$HOME/ahead/file.txt"
commit_git ahead "A commit that has not been pushed"
