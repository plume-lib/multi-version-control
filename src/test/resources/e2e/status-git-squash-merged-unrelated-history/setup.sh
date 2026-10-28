# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote clone
clone_git remote other
# The main branch adds and removes page.txt, then merges a history that shares nothing with it.
echo "a page" > other/page.txt
commit_git other "Add a page"
git -C other rm -q page.txt
git -C other commit -q -m "Remove the page"
git -C other switch -q --orphan pages
echo "a page" > other/page.txt
commit_git other "Start the pages"
git -C other push -q origin pages
git -C other switch -q master
git -C other merge -q --allow-unrelated-histories -m "Merge the pages" pages
git -C other push -q origin master
git -C clone fetch -q
git -C clone switch -q -c feature origin/pages
echo "changed page" > clone/page.txt
commit_git clone "Change the page"
# Squash-merge the feature branch into master, as GitHub does.
echo "changed page" > other/page.txt
commit_git other "Change the page (#1)"
git -C other push -q origin master
git -C other push -q origin --delete pages
git -C clone fetch -q -p
