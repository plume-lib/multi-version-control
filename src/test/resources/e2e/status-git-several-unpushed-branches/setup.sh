# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote clone
git -C clone switch -q -c feature/x
echo "second line" >> clone/file.txt
commit_git clone "A commit that has not been pushed"
git -C clone branch same
git -C clone switch -q -c pushed master
echo "pushed" > clone/pushed.txt
commit_git clone "A commit that has been pushed"
git -C clone push -q -u origin pushed
git -C clone switch -q -c other master
echo "other" > clone/other.txt
commit_git clone "Another commit that has not been pushed"
