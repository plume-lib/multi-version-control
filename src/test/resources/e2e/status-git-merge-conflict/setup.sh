# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote clone
echo "local change" > clone/file.txt
commit_git clone "A local commit"
# Commit a conflicting change to the remote repository, through a second clone.
clone_git remote other
echo "remote change" > other/file.txt
commit_git other "A conflicting commit"
git -C other push -q origin master
git -C clone fetch -q
git -C clone merge -q origin/master > /dev/null || true
