# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_hg_repo upstream
hg clone -q "$HOME/upstream" "$HOME/clone"
echo "second line" >> "$HOME/upstream/file.txt"
hg -R "$HOME/upstream" commit -q -m "A commit to pull"
