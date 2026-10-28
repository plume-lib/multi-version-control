# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote clone
# A branch whose commit does not exist.
echo 0123456789012345678901234567890123456789 > clone/.git/refs/heads/broken
