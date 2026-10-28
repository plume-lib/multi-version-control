# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote clone
git -C clone switch -q --orphan pages
echo "a page" > clone/page.txt
commit_git clone "A branch that shares no history with the main branch"
