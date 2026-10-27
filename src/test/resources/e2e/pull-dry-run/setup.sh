# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
mkdir -p "$HOME/cvs/mod/CVS" "$HOME/hg/hg/.hg" "$HOME/svn/proj/.svn"
git init -q "$HOME/git/options"
git -C "$HOME/git/options" remote add origin https://github.com/plume-lib/options.git
