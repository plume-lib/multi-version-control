# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote clone
clone_git remote work
git clone -q --bare remote.git other.git
git -C clone remote add other "$HOME/other.git"
# GitHub keeps each pull request's head in a ref like these.
git -C remote.git update-ref refs/pull/1/head master
echo "pull request 2" > work/file.txt
commit_git work "Pull request 2"
git -C work push -q origin HEAD:refs/pull/2/head
# An earlier pull fetched pull requests 3 and 4.
git -C clone update-ref refs/pull/origin/3 master
git -C clone update-ref refs/pull/origin/4 master
echo "pull request 3" > work/file.txt
commit_git work "Pull request 3"
git -C work push -q origin HEAD:refs/pull/3/head
git -C other.git update-ref refs/pull/5/head master
