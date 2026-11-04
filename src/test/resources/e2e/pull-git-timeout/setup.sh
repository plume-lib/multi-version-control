# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote clone
# A local branch whose commit is on no remote branch, so that the program would fetch the
# pull-request heads of the GitHub remote.
echo "unpushed" > clone/file.txt
commit_git clone "Unpushed"
git -C clone remote set-url origin https://github.com/example/remote.git
# "exec" matters: the program can only kill the process that it started, and a surviving child
# would hold the output pipe open.
printf '#!/bin/sh\nexec sleep 60\n' > "$HOME/slow-git"
chmod +x "$HOME/slow-git"
