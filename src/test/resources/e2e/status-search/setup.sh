# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote clean
clone_git remote projects/modified
echo "second line" >> projects/modified/file.txt
