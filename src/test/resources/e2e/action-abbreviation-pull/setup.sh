# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
git init -q "$HOME/git/options"
git -C "$HOME/git/options" remote add origin https://github.com/plume-lib/options.git
