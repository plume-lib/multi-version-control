# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote clone
clone_git remote other
git -C clone switch -q -c feature
echo "changed line" > clone/file.txt
commit_git clone "Change the line"
git -C clone push -q origin feature
# Another commit on the pull request, which this clone never fetched.
git -C other fetch -q origin feature
git -C other switch -q feature
echo "final line" > other/file.txt
commit_git other "Change the line further"
git -C other push -q origin feature
# GitHub keeps each pull request's head, even after the branch is deleted.
git -C other push -q origin feature:refs/pull/1/head
# Squash-merge the pull request and delete its branch.
git -C other switch -q master
echo "final line" > other/file.txt
commit_git other "Change the line (#1)"
git -C other push -q origin master
git -C other push -q origin --delete feature
git -C clone fetch -q -p
git -C clone fetch -q origin "+refs/pull/*/head:refs/pull/origin/*"
