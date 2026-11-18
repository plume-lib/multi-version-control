# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_svn_checkout repo wc
echo "to be deleted" > wc/deleted.txt
echo "to be removed" > wc/missing.txt
svn -q add wc/deleted.txt wc/missing.txt
svn -q commit -m "More files" wc
echo "new" > wc/added.txt
svn -q add wc/added.txt
svn -q delete wc/deleted.txt
rm wc/missing.txt
echo "new" > wc/untracked.txt
