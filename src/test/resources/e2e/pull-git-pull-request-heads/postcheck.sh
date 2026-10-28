# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
git -C clone for-each-ref --format='%(refname) %(subject)' refs/pull
