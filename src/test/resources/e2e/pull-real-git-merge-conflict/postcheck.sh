# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
git -C "$HOME/clone" status --porcelain
