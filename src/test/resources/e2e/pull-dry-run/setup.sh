# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
mkdir -p "$HOME/cvs/mod/CVS" "$HOME/hg/hg/.hg" "$HOME/svn/proj/.svn"
git init -q "$HOME/git/options"
git -C "$HOME/git/options" remote add origin https://github.com/plume-lib/options.git
# A commit that is on no remote, so that "pull" looks for pull-request heads.
git -C "$HOME/git/options" commit -q --allow-empty -m "A commit"
