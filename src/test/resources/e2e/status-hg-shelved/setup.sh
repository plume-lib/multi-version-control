# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_hg_repo upstream
hg clone -q "$HOME/upstream" "$HOME/clone"
echo "second line" >> clone/file.txt
# "hg shelve" is part of Mercurial since version 5.0; for an older Mercurial, enable the extension.
printf '[extensions]\nshelve =\n' >> clone/.hg/hgrc
hg -R clone shelve -q
