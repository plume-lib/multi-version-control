# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote clone
# Commit to the remote repository, through a second clone.
clone_git remote other
echo "second line" >> "$HOME/other/file.txt"
commit_git other "A commit to pull"
git -C "$HOME/other" push -q origin master
