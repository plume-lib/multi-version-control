# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
cat "$HOME/parent/new-clone/file.txt"
hg -R "$HOME/parent/new-clone" paths default
