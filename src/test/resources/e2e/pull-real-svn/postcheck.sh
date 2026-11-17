# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
cat "$HOME/wc/file.txt"
cat "$HOME/missing-file/file.txt"
