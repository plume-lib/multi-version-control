# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
if [ -e "$HOME/a" ] ; then echo "$HOME/a exists" ; else echo "no directory was created" ; fi
