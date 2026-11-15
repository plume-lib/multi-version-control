# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote clone
git -C clone switch -q -c feature
echo "changed line" > clone/file.txt
commit_git clone "Change the line"
git -C clone push -q -u origin feature
git -C clone commit -q --amend -m "Change the line, with a better message"
