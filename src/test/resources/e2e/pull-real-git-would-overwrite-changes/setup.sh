# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote clone
clone_git remote other
echo "second line" >> "$HOME/other/file.txt"
commit_git other "A commit to pull"
git -C "$HOME/other" push -q origin master
# An uncommitted change to the same file that the pull would update.
echo "a conflicting line" >> "$HOME/clone/file.txt"
