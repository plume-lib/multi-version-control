# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote clone
git -C clone config branch.master.merge "refs/heads/a b"
echo "second line" >> clone/file.txt
commit_git clone "A commit that has not been pushed"
