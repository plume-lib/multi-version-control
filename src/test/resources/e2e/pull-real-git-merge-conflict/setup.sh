# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote clone
# Without a pull.rebase setting, git refuses to reconcile divergent branches.
git -C clone config pull.rebase false
echo "local change" > clone/file.txt
commit_git clone "A local commit"
# Commit a conflicting change to the remote repository, through a second clone.
clone_git remote other
echo "remote change" > other/file.txt
commit_git other "A conflicting commit"
git -C other push -q origin master
