# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_hg_repo upstream
hg clone -q "$HOME/upstream" "$HOME/modified"
hg clone -q "$HOME/upstream" "$HOME/unpushed"
echo "second line" >> "$HOME/modified/file.txt"
echo "new" > "$HOME/modified/untracked.txt"
echo "second line" >> "$HOME/unpushed/file.txt"
hg -R "$HOME/unpushed" commit -q -m "A commit that has not been pushed"
