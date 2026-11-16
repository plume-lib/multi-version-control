# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote clone
clone_git remote other
# A long-lived branch, other than the main branch, into which pull requests land.
git -C other switch -q -c integration
git -C other push -q -u origin integration
git -C clone fetch -q
git -C clone switch -q integration
git -C clone switch -q -c feature
echo "changed line" > clone/file.txt
commit_git clone "Change the line"
# Squash-merge the feature branch into the integration branch, as GitHub does.
echo "changed line" > other/file.txt
commit_git other "Change the line (#1)"
git -C other push -q origin integration
git -C clone fetch -q
