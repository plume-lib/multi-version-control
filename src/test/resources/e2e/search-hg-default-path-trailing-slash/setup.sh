# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_fake_hg_clone slash https://www.mercurial-scm.org/repo/hg/
mkdir -p "$HOME/no-paths/.hg"
printf '[ui]\nusername = Test User\n' > "$HOME/no-paths/.hg/hgrc"
