# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote clone
clone_git remote other
git -C clone switch -q -c feature
echo "changed line" > clone/file.txt
commit_git clone "Change the line"
# Squash-merge the feature branch into master, as GitHub does.
echo "changed line" > other/file.txt
commit_git other "Change the line (#1)"
git -C other push -q origin master
git -C clone fetch -q
