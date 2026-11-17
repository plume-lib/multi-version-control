# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
# Only the version control subdirectories; a dry run runs no version control program.
mkdir -p "$HOME/cvs/mod/CVS" "$HOME/git/options/.git" "$HOME/hg/hg/.hg" "$HOME/svn/proj/.svn"
