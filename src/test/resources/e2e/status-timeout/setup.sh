# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote clone
# "exec" matters: the program can only kill the process that it started, and a surviving child
# would hold the output pipe open.
printf '#!/bin/sh\nexec sleep 60\n' > "$HOME/slow-git"
chmod +x "$HOME/slow-git"
