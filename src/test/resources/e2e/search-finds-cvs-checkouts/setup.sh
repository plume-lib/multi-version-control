# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_cvs_checkout project myproject
make_cvs_checkout project/subdir myproject/subdir
make_cvs_checkout other-project some/deep/path/other-project
