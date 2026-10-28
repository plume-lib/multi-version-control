# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote clone
clone_git remote work
git clone -q --bare remote.git other.git
git -C clone remote add other "$HOME/other.git"
# A remote is on GitHub if its URL is.
git -C clone config url."$HOME/remote.git".insteadOf https://github.com/example/remote.git
git -C clone remote set-url origin https://github.com/example/remote.git
# A local branch whose commit is on no remote branch.
git -C clone switch -q -c feature
echo "feature" > clone/file.txt
commit_git clone "Feature"
git -C clone switch -q master
# GitHub keeps each pull request's head in a ref like these.
git -C clone push -q origin feature:refs/pull/1/head
echo "pull request 2" > work/file.txt
commit_git work "Pull request 2"
git -C work push -q origin HEAD:refs/pull/2/head
# An earlier pull fetched pull requests 3 and 4.
git -C clone update-ref refs/pull/origin/3 master
git -C clone update-ref refs/pull/origin/4 master
echo "pull request 3" > work/file.txt
commit_git work "Pull request 3"
git -C work push -q origin HEAD:refs/pull/3/head
# Pull request 5 landed with a merge commit, so its head is in the main branch.
git -C remote.git update-ref refs/pull/5/head master
git -C clone push -q other feature:refs/pull/6/head
# An earlier pull fetched a pull request of a remote that has since been removed.
git -C clone update-ref refs/pull/gone/7 master
