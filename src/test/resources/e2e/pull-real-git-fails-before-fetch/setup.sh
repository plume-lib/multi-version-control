# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote clone
clone_git remote other
git -C other push -q origin master:feature
git -C clone fetch -q
git -C other push -q origin :feature
echo "second line" >> other/file.txt
commit_git other "A commit to pull"
git -C other push -q origin master
# "git pull" refuses to rebase onto a working tree that has uncommitted changes, before it fetches.
git -C clone config pull.rebase true
echo "an uncommitted line" >> clone/file.txt
