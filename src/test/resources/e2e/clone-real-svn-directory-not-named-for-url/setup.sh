# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
svnadmin create repo
svn -q mkdir -m "Create trunk" "file://$HOME/repo/trunk"
svn -q checkout "file://$HOME/repo/trunk" seed
echo "first line" > seed/file.txt
svn -q add seed/file.txt
svn -q commit -m "Initial commit" seed
rm -rf seed
