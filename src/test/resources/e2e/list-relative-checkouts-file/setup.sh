# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
mkdir config
printf 'GITREPOS: https://github.com/plume-lib/options.git\n~/options\n' > config/my-checkouts
