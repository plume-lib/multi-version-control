# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
cat "$HOME/parent/new-clone/file.txt"
git -C "$HOME/parent/new-clone" config --get remote.origin.url
