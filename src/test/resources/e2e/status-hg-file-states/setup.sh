# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_hg_repo upstream
echo "to be removed" > upstream/removed.txt
echo "to be deleted" > upstream/missing.txt
hg -R upstream add -q upstream/removed.txt upstream/missing.txt
hg -R upstream commit -q -m "More files"
hg clone -q "$HOME/upstream" "$HOME/clone"
echo "new" > clone/added.txt
hg -R clone add -q clone/added.txt
hg -R clone remove -q clone/removed.txt
rm clone/missing.txt
echo "new" > clone/untracked.txt
