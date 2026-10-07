# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_hg_repo upstream
hg clone -q "$HOME/upstream" "$HOME/clone-with-default-path"
