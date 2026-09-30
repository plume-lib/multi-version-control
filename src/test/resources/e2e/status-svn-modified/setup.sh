# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_svn_checkout repo wc
echo "second line" >> "$HOME/wc/file.txt"
echo "new" > "$HOME/wc/untracked.txt"
