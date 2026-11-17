# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_svn_checkout repo other
svn -q checkout "file://$HOME/repo" wc
# Commit to the repository, through the other working copy.
echo "second line" >> other/file.txt
svn -q commit -m "A commit to pull" other
# A working copy from which a versioned file has been deleted without "svn delete".  Updating
# restores it.
svn -q checkout "file://$HOME/repo" missing-file
rm missing-file/file.txt
