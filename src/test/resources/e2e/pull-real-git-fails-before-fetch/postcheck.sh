# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
git -C clone branch -r
git -C clone log -1 --format=%s origin/master
