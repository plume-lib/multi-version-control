# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote clone
git -C clone switch -q -c feature
echo "changed line" > clone/file.txt
commit_git clone "Change the line"
git -C clone push -q origin feature
# GitHub keeps each pull request's head, even after the branch is deleted.
git -C clone push -q origin feature:refs/pull/1/head
# Close the pull request without landing it, and delete its branch.
git -C clone push -q origin --delete feature
git -C clone fetch -q origin "+refs/pull/*/head:refs/pull/origin/*"
