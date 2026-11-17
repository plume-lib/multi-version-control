# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
cat "$HOME/parent/trunk/file.txt"
svn info --show-item url "$HOME/parent/trunk"
