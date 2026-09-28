# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
# The parent directory cannot be created, because part of its path is a regular file.
echo "not a directory" > "$HOME/a-file"
